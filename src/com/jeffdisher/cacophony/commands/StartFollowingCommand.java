package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.HighLevelCache;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public record StartFollowingCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		HighLevelCache cache = HighLevelCache.fromLocal(local);
		FollowIndex followIndex = local.loadFollowIndex();
		
		// We need to first verify that we aren't already following them.
		IpfsFile lastRoot = followIndex.getLastFetchedRoot(_publicKey);
		Assert.assertTrue(null == lastRoot);
		
		// Then, do the initial resolve of the key to make sure the network thinks it is valid.
		IpfsFile indexRoot = remote.resolvePublicKey(_publicKey);
		Assert.assertTrue(null != indexRoot);
		
		// Now, cache the root meta-data structures.
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, indexRoot);
		StreamIndex streamIndex = GlobalData.deserializeIndex(remote.readData(indexRoot));
		Assert.assertTrue(1 == streamIndex.getVersion());
		IpfsFile descriptionHash = IpfsFile.fromIpfsCid(streamIndex.getDescription());
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, descriptionHash);
		IpfsFile recommendationsHash = IpfsFile.fromIpfsCid(streamIndex.getRecommendations());
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, recommendationsHash);
		IpfsFile recordsHash = IpfsFile.fromIpfsCid(streamIndex.getRecords());
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, recordsHash);
		StreamDescription description = GlobalData.deserializeDescription(remote.readData(descriptionHash));
		IpfsFile pictureHash = IpfsFile.fromIpfsCid(description.getPicture());
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, pictureHash);
		
		// Populate the initial cache records.
		_populateCachedRecords(remote, cache, followIndex, GlobalData.deserializeRecords(remote.readData(recordsHash)));
		
		// Then, save this data back to the FollowingIndex.
		followIndex.addFollowingWithInitialState(_publicKey, indexRoot);
	}


	private void _populateCachedRecords(RemoteActions remote, HighLevelCache cache, FollowIndex followIndex, StreamRecords newRecords) throws IOException
	{
		// Note that we always cache the CIDs of the records, whether or not we cache the leaf data files within (since these record elements are tiny).
		// For now, we just add the record CIDs, not the leaf elements.
		// TODO:  Fetch the leaf elements and apply the correct decay algorithm to expire cache elements.
		for (String rawCid : newRecords.getRecord())
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(rawCid);
			cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, cid);
			// TODO: Make this population use the filter and cache algorithm.
			StreamRecord record = GlobalData.deserializeRecord(remote.readData(cid));
			for (DataElement elt : record.getElements().getElement())
			{
				IpfsFile eltCid = IpfsFile.fromIpfsCid(elt.getCid());
				cache.addToFollowCache(_publicKey, HighLevelCache.Type.FILE, eltCid);
			}
		}
	}
}
