package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public record ListChannelEntriesCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		StreamIndex index = HighLevelIdioms.readIndexForKey(remote, _channelPublicKey, null);
		byte[] rawRecords = remote.readData(IpfsFile.fromIpfsCid(index.getRecords()));
		StreamRecords records = GlobalData.deserializeRecords(rawRecords);
		
		// Walk the elements, reading each element.
		for (String recordCid : records.getRecord())
		{
			byte[] rawRecord = remote.readData(IpfsFile.fromIpfsCid(recordCid));
			StreamRecord record = GlobalData.deserializeRecord(rawRecord);
			System.out.println("element " + recordCid + ": " + record.getName());
			DataArray array = record.getElements();
			for (DataElement element : array.getElement())
			{
				System.out.println("\t" + element.getCid() + " - " + element.getMime());
			}
		}
	}
}
