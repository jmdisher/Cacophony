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
import com.jeffdisher.cacophony.logic.LocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public record RemoveEntryFromThisChannelCommand(IpfsFile _elementCid) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, LocalActions local) throws IOException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		
		// The general idea here is that we want to unpin all data elements associated with this, but only after we update the record stream and channel index (since broken data will cause issues for followers).
		
		// Read the existing StreamIndex.
		IpfsKey publicKey = remote.getPublicKey();
		StreamIndex index = HighLevelIdioms.readIndexForKey(remote, publicKey);
		
		// Read the existing stream so we can append to it (we do this first just to verify integrity is fine).
		byte[] rawRecords = remote.readData(IpfsFile.fromIpfsCid(index.getRecords()));
		StreamRecords records = GlobalData.deserializeRecords(rawRecords);
		
		// Make sure that we actually have the record.
		boolean didFind = false;
		String search = _elementCid.cid().toBase58();
		int foundIndex = 0;
		for (String cid : records.getRecord())
		{
			if (search.equals(cid))
			{
				didFind = true;
				break;
			}
			foundIndex += 1;
		}
		
		if (didFind)
		{
			// Update the record list and stream index.
			records.getRecord().remove(foundIndex);
			rawRecords = GlobalData.serializeRecords(records);
			IpfsFile newCid = remote.saveData(rawRecords);
			index.setRecords(newCid.cid().toBase58());
			HighLevelIdioms.saveAndPublishIndex(executor, remote, index);
			
			// Finally, unpin the entries (we need to unpin them all since we own them so we added them all).
			byte[] rawRecord = remote.readData(_elementCid);
			StreamRecord record = GlobalData.deserializeRecord(rawRecord);
			DataArray array = record.getElements();
			for (DataElement element : array.getElement())
			{
				IpfsFile cid = IpfsFile.fromIpfsCid(element.getCid());
				remote.unpin(cid);
			}
			remote.unpin(_elementCid);
		}
		else
		{
			executor.fatalError(new Exception("CID " + _elementCid + " not found in record list"));
		}
	}
}
