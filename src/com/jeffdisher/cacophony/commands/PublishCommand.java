package com.jeffdisher.cacophony.commands;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.Assert;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.HighLevelCache;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public record PublishCommand(String _name, String _discussionUrl, ElementSubCommand[] _elements) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		HighLevelCache cache = HighLevelCache.fromLocal(local);
		
		// Read the existing StreamIndex.
		IpfsKey publicKey = remote.getPublicKey();
		IpfsFile[] previousIndexFile = new IpfsFile[1];
		StreamIndex index = HighLevelIdioms.readIndexForKey(remote, publicKey, previousIndexFile);
		cache.removeFromThisCache(previousIndexFile[0]);
		
		// Read the existing stream so we can append to it (we do this first just to verify integrity is fine).
		byte[] rawRecords = remote.readData(IpfsFile.fromIpfsCid(index.getRecords()));
		StreamRecords records = GlobalData.deserializeRecords(rawRecords);
		
		// Upload the elements.
		Assert.assertTrue(_elements.length > 0);
		DataArray array = new DataArray();
		for (ElementSubCommand elt : _elements)
		{
			// Upload the file.
			// TODO:  Use a stream to upload.
			byte[] raw = Files.readAllBytes(elt.filePath().toPath());
			IpfsFile uploaded = HighLevelIdioms.saveData(executor, remote, raw);
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
		IpfsFile indexHash = HighLevelIdioms.saveAndPublishIndex(executor, remote, index);
		cache.uploadedToThisCache(indexHash);
	}
}
