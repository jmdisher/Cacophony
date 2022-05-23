package com.jeffdisher.cacophony.commands;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
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
		HighLevelCache cache = new HighLevelCache(pinCache, connection);
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, connection, localIndex.keyName());
		LoadChecker checker = new LoadChecker(remote, pinCache, connection);
		
		// Read the existing StreamIndex.
		IpfsKey publicKey = remote.getPublicKey();
		IpfsFile rootToLoad = localIndex.lastPublishedIndex();
		Assert.assertTrue(null != rootToLoad);
		StreamIndex index = GlobalData.deserializeIndex(checker.loadCached(rootToLoad));
		
		// Read the existing stream so we can append to it (we do this first just to verify integrity is fine).
		byte[] rawRecords = checker.loadCached(IpfsFile.fromIpfsCid(index.getRecords()));
		StreamRecords records = GlobalData.deserializeRecords(rawRecords);
		
		// Upload the elements.
		DataArray array = new DataArray();
		for (ElementSubCommand elt : _elements)
		{
			IOperationLog eltLog = environment.logOperation("-Element: " + elt);
			// Upload the file.
			IpfsFile uploaded;
			try {
				FileInputStream inputStream = new FileInputStream(elt.filePath());
				uploaded = remote.saveStream(inputStream);
				inputStream.close();
			}
			catch (IOException e)
			{
				// Failure to read this file is a static usage error.
				throw new UsageException("Unable to read file: " + elt.filePath());
			}
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
		IpfsFile recordHash = remote.saveData(rawRecord);
		cache.uploadedToThisCache(recordHash);
		
		records.getRecord().add(recordHash.toSafeString());
		
		// Save the updated records and index.
		rawRecords = GlobalData.serializeRecords(records);
		IpfsFile recordsHash = remote.saveData(rawRecords);
		cache.uploadedToThisCache(recordsHash);
		
		// Update, save, and publish the new index.
		index.setRecords(recordsHash.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		IpfsFile indexHash = CommandHelpers.serializeSaveAndPublishIndex(remote, index);
		
		// By this point, we have completed the essential network operations (everything else is local state and network clean-up).
		// Update the local index.
		local.storeSharedIndex(new LocalIndex(localIndex.ipfsHost(), localIndex.keyName(), indexHash));
		cache.uploadedToThisCache(indexHash);
		
		// Remove the old root.
		cache.removeFromThisCache(rootToLoad);
		local.writeBackConfig();
		log.finish("Publish completed!");
	}


	private static long _currentUtcEpochSeconds()
	{
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return now.toEpochSecond();
	}
}
