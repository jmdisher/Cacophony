package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FutureSave;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record PublishCommand(String _name, String _description, String _discussionUrl, ElementSubCommand[] _elements) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue(null != _name);
		Assert.assertTrue(null != _description);
		
		IOperationLog log = environment.logOperation("Publish: " + this);
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = local.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = local.loadGlobalPinCache();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler);
		CleanupData cleanup = _runCore(environment, scheduler, connection, localIndex, pinCache, cache);
		
		// By this point, we have completed the essential network operations (everything else is local state and network clean-up).
		_runFinish(environment, local, localIndex, cache, cleanup);
		log.finish("Publish completed!");
	}

	private CleanupData _runCore(IEnvironment environment, INetworkScheduler scheduler, IConnection connection, LocalIndex localIndex, GlobalPinCache pinCache, HighLevelCache cache) throws UsageException, IpfsConnectionException
	{
		LoadChecker checker = new LoadChecker(scheduler, pinCache, connection);
		
		// Read the existing StreamIndex.
		IpfsKey publicKey = scheduler.getPublicKey();
		IpfsFile rootToLoad = localIndex.lastPublishedIndex();
		Assert.assertTrue(null != rootToLoad);
		StreamIndex index = checker.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		
		// Read the existing stream so we can append to it (we do this first just to verify integrity is fine).
		StreamRecords records = checker.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data)).get();
		
		// Upload the elements.
		List<FutureSave> futureSaves = new ArrayList<>();
		for (ElementSubCommand elt : _elements)
		{
			IOperationLog eltLog = environment.logOperation("-Element: " + elt);
			// Upload the file.
			try {
				FileInputStream inputStream = new FileInputStream(elt.filePath());
				FutureSave save = scheduler.saveStream(inputStream, true);
				futureSaves.add(save);
			}
			catch (IOException e)
			{
				// Failure to read this file is a static usage error.
				throw new UsageException("Unable to read file: " + elt.filePath());
			}
			eltLog.finish("-in progress...");
		}
		
		DataArray array = new DataArray();
		Iterator<FutureSave> futureIterator = futureSaves.iterator();
		for (ElementSubCommand elt : _elements)
		{
			IOperationLog eltLog = environment.logOperation("-Element: " + elt);
			// Wait for file upload.
			FutureSave save = futureIterator.next();
			IpfsFile uploaded = save.get();
			cache.uploadedToThisCache(uploaded);
			
			DataElement element = new DataElement();
			element.setCid(uploaded.toSafeString());
			element.setMime(elt.mime());
			element.setHeight(elt.height());
			element.setWidth(elt.width());
			if (elt.isSpecialImage())
			{
				element.setSpecial(ElementSpecialType.IMAGE);
			}
			array.getElement().add(element);
			eltLog.finish("-Done!");
		}
		Assert.assertTrue(!futureIterator.hasNext());
		StreamRecord record = new StreamRecord();
		record.setName(_name);
		record.setDescription(_description);
		if (null != _discussionUrl)
		{
			record.setDiscussion(_discussionUrl);
		}
		record.setElements(array);
		record.setPublisherKey(publicKey.toPublicKey());
		// The published time is in seconds since the Epoch, in UTC.
		record.setPublishedSecondsUtc(_currentUtcEpochSeconds());
		byte[] rawRecord = GlobalData.serializeRecord(record);
		IpfsFile recordHash = scheduler.saveStream(new ByteArrayInputStream(rawRecord), true).get();
		cache.uploadedToThisCache(recordHash);
		
		records.getRecord().add(recordHash.toSafeString());
		
		// Save the updated records and index.
		byte[] rawRecords = GlobalData.serializeRecords(records);
		IpfsFile recordsHash = scheduler.saveStream(new ByteArrayInputStream(rawRecords), true).get();
		cache.uploadedToThisCache(recordsHash);
		
		// Update, save, and publish the new index.
		index.setRecords(recordsHash.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		IpfsFile indexHash = CommandHelpers.serializeSaveAndPublishIndex(environment, scheduler, index);
		return new CleanupData(indexHash, rootToLoad);
	}

	private void _runFinish(IEnvironment environment, LocalConfig local, LocalIndex localIndex, HighLevelCache cache, CleanupData data)
	{
		// Update the local index.
		local.storeSharedIndex(new LocalIndex(localIndex.ipfsHost(), localIndex.keyName(), data.indexHash));
		cache.uploadedToThisCache(data.indexHash);
		
		// Remove the old root.
		_safeRemove(environment, cache, data.oldRootHash);
		local.writeBackConfig();
	}

	private static void _safeRemove(IEnvironment environment, HighLevelCache cache, IpfsFile file)
	{
		try
		{
			cache.removeFromThisCache(file).get();
		}
		catch (IpfsConnectionException e)
		{
			environment.logError("WARNING: Error unpinning " + file + ".  This will need to be done manually.");
		}
	}

	private static long _currentUtcEpochSeconds()
	{
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return now.toEpochSecond();
	}


	private static record CleanupData(IpfsFile indexHash, IpfsFile oldRootHash) {}
}
