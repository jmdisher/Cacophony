package com.jeffdisher.cacophony.commands;

import java.io.FileInputStream;
import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.HighLevelCache;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public record PublishCommand(String _name, String _description, String _discussionUrl, ElementSubCommand[] _elements) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException, CacophonyException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(executor, local);
		LoadChecker checker = new LoadChecker(remote, local);
		HighLevelCache cache = HighLevelCache.fromLocal(local);
		
		// Read the existing StreamIndex.
		IpfsKey publicKey = remote.getPublicKey();
		LocalIndex localIndex = local.readIndex();
		IpfsFile rootToLoad = localIndex.lastPublishedIndex();
		Assert.assertTrue(null != rootToLoad);
		StreamIndex index = GlobalData.deserializeIndex(checker.loadCached(rootToLoad));
		
		// Read the existing stream so we can append to it (we do this first just to verify integrity is fine).
		byte[] rawRecords = checker.loadCached(IpfsFile.fromIpfsCid(index.getRecords()));
		StreamRecords records = GlobalData.deserializeRecords(rawRecords);
		
		// Upload the elements.
		Assert.assertTrue(_elements.length > 0);
		DataArray array = new DataArray();
		for (ElementSubCommand elt : _elements)
		{
			// Upload the file.
			FileInputStream inputStream = new FileInputStream(elt.filePath());
			IpfsFile uploaded = HighLevelIdioms.saveStream(executor, remote, inputStream);
			inputStream.close();
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
		record.setPublishedSecondsUtc((int)(System.currentTimeMillis() / 1000));
		byte[] rawRecord = GlobalData.serializeRecord(record);
		IpfsFile recordHash = HighLevelIdioms.saveData(executor, remote, rawRecord);
		cache.uploadedToThisCache(recordHash);
		
		records.getRecord().add(recordHash.toSafeString());
		
		// Save the updated records and index.
		rawRecords = GlobalData.serializeRecords(records);
		IpfsFile recordsHash = HighLevelIdioms.saveData(executor, remote, rawRecords);
		cache.uploadedToThisCache(recordsHash);
		
		// Update, save, and publish the new index.
		index.setRecords(recordsHash.toSafeString());
		IpfsFile indexHash = HighLevelIdioms.saveAndPublishIndex(executor, remote, local, index);
		cache.uploadedToThisCache(indexHash);
		
		// Remove the old root.
		cache.removeFromThisCache(rootToLoad);
	}
}
