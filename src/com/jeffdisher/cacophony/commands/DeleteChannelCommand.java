package com.jeffdisher.cacophony.commands;

import com.jeffdisher.breakwater.utilities.Assert;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;


public record DeleteChannelCommand() implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		if (null == context.getSelectedKey())
		{
			throw new UsageException("Channel must first be created with --createNewChannel");
		}
		
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			IpfsFile indexCid = access.getLastRootElement();
			StreamIndex index = access.loadCached(indexCid, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
			
			IpfsFile recommendationsCid = IpfsFile.fromIpfsCid(index.getRecommendations());
			// Recommendations has no other info so we don't need to load it.
			access.unpin(recommendationsCid);
			
			IpfsFile descriptionCid = IpfsFile.fromIpfsCid(index.getDescription());
			StreamDescription description = access.loadCached(descriptionCid, (byte[] data) -> GlobalData.deserializeDescription(data)).get();
			_handleDescription(access, description);
			access.unpin(descriptionCid);
			
			IpfsFile recordsCid = IpfsFile.fromIpfsCid(index.getRecords());
			StreamRecords records = access.loadCached(recordsCid, (byte[] data) -> GlobalData.deserializeRecords(data)).get();
			_handleRecords(access, records);
			access.unpin(recordsCid);
			
			access.unpin(indexCid);
			access.deleteChannelData();
			// We also want to clean up the context.
			context.removeKey(context.keyName);
		}
		catch (FailedDeserializationException e)
		{
			// This is our own well-formed data so we don't expect this error.
			throw Assert.unexpected(e);
		}
		return None.NONE;
	}


	private void _handleDescription(IWritingAccess access, StreamDescription description) throws IpfsConnectionException
	{
		// We just need the user pic.
		IpfsFile pic = IpfsFile.fromIpfsCid(description.getPicture());
		access.unpin(pic);
	}

	private void _handleRecords(IWritingAccess access, StreamRecords records) throws FailedDeserializationException, IpfsConnectionException
	{
		// We need to walk all the records and then walk every leaf in each one.
		for (String rawCid : records.getRecord())
		{
			IpfsFile recordCid = IpfsFile.fromIpfsCid(rawCid);
			StreamRecord record = access.loadCached(recordCid, (byte[] data) -> GlobalData.deserializeRecord(data)).get();
			_handleRecord(access, record);
		}
	}


	private void _handleRecord(IWritingAccess access, StreamRecord record) throws IpfsConnectionException
	{
		for (DataElement leaf : record.getElements().getElement())
		{
			IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
			access.unpin(leafCid);
		}
	}
}
