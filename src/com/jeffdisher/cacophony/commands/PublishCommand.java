package com.jeffdisher.cacophony.commands;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.Assert;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.LocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.utils.Types;

import io.ipfs.multihash.Multihash;


public record PublishCommand(String _name, String _discussionUrl, ElementSubCommand[] _elements) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, LocalActions local) throws IOException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		
		// Read the existing StreamIndex.
		Multihash publicKey = remote.getPublicKey();
		StreamIndex index = HighLevelIdioms.readIndexForKey(remote, publicKey);
		
		// Read the existing stream so we can append to it (we do this first just to verify integrity is fine).
		byte[] rawRecords = remote.readData(Types.fromIpfsCid(index.getRecords()));
		StreamRecords records = GlobalData.deserializeRecords(rawRecords);
		
		// Upload the elements.
		// TODO:  Eventually support optional arguments and multiple files.
		Assert.assertNull(_discussionUrl);
		Assert.assertTrue(1 == _elements.length);
		Assert.assertNull(_elements[0].codec());
		Assert.assertTrue(0 == _elements[0].width());
		Assert.assertTrue(0 == _elements[0].height());
		
		// Upload the file.
		// TODO:  Use a stream to upload.
		byte[] raw = Files.readAllBytes(_elements[0].filePath().toPath());
		Multihash uploaded = HighLevelIdioms.saveData(executor, remote, raw);
		DataElement element = new DataElement();
		element.setCid(uploaded.toBase58());
		element.setMime(_elements[0].mime());
		DataArray array = new DataArray();
		array.getElement().add(element);
		StreamRecord record = new StreamRecord();
		record.setName(_name);
		record.setElements(array);
		record.setPublisherKey(publicKey.toBase58());
		record.setPublishedSecondsUtc((int)(System.currentTimeMillis() / 1000));
		byte[] rawRecord = GlobalData.serializeRecord(record);
		Multihash recordHash = HighLevelIdioms.saveData(executor, remote, rawRecord);
		
		records.getRecord().add(recordHash.toBase58());
		
		// Save the updated records and index.
		rawRecords = GlobalData.serializeRecords(records);
		Multihash recordsHash = HighLevelIdioms.saveData(executor, remote, rawRecords);
		
		// Update, save, and publish the new index.
		index.setRecords(recordsHash.toBase58());
		HighLevelIdioms.saveAndPublishIndex(executor, remote, index);
	}
}
