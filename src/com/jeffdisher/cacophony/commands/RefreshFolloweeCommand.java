package com.jeffdisher.cacophony.commands;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

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


public record RefreshFolloweeCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		HighLevelCache cache = HighLevelCache.fromLocal(local);
		FollowIndex followIndex = local.loadFollowIndex();
		
		// We need to first verify that we aren't already following them.
		IpfsFile lastRoot = followIndex.getLastFetchedRoot(_publicKey);
		Assert.assertTrue(null != lastRoot);
		
		// Then, do the initial resolve of the key to make sure the network thinks it is valid.
		IpfsFile indexRoot = remote.resolvePublicKey(_publicKey);
		Assert.assertTrue(null != indexRoot);
		
		// See if anything changed.
		if (lastRoot.equals(indexRoot))
		{
			System.out.println("Follow index unchanged (" + lastRoot + ")");
		}
		else
		{
			System.out.println("Follow index changed (" + lastRoot + ") -> (" + indexRoot + ")");
			
			// Cache the new root and remove the old one.
			cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, indexRoot);
			StreamIndex oldIndex = GlobalData.deserializeIndex(remote.readData(lastRoot));
			StreamIndex newIndex = GlobalData.deserializeIndex(remote.readData(indexRoot));
			Assert.assertTrue(1 == oldIndex.getVersion());
			Assert.assertTrue(1 == newIndex.getVersion());
			if (!oldIndex.getDescription().equals(newIndex.getDescription()))
			{
				IpfsFile oldDescriptionCid = IpfsFile.fromIpfsCid(oldIndex.getDescription());
				IpfsFile newDescriptionCid = IpfsFile.fromIpfsCid(newIndex.getDescription());
				cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, newDescriptionCid);
				StreamDescription oldDescription = GlobalData.deserializeDescription(remote.readData(oldDescriptionCid));
				StreamDescription newDescription = GlobalData.deserializeDescription(remote.readData(newDescriptionCid));
				if (!oldDescription.getPicture().equals(newDescription.getPicture()))
				{
					cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, IpfsFile.fromIpfsCid(newDescription.getPicture()));
					cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, IpfsFile.fromIpfsCid(oldDescription.getPicture()));
				}
				cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, oldDescriptionCid);
			}
			if (!oldIndex.getRecommendations().equals(newIndex.getRecommendations()))
			{
				IpfsFile oldRecommendationsCid = IpfsFile.fromIpfsCid(oldIndex.getRecommendations());
				IpfsFile newRecommendationsCid = IpfsFile.fromIpfsCid(newIndex.getRecommendations());
				cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, newRecommendationsCid);
				cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, oldRecommendationsCid);
			}
			if (!oldIndex.getRecords().equals(newIndex.getRecords()))
			{
				IpfsFile oldRecordsCid = IpfsFile.fromIpfsCid(oldIndex.getRecords());
				IpfsFile newRecordsCid = IpfsFile.fromIpfsCid(newIndex.getRecords());
				cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, newRecordsCid);
				StreamRecords oldRecords = GlobalData.deserializeRecords(remote.readData(oldRecordsCid));
				StreamRecords newRecords = GlobalData.deserializeRecords(remote.readData(newRecordsCid));
				_updateCachedRecords(remote, cache, followIndex, oldRecords, newRecords);
				cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, oldRecordsCid);
			}
			cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, lastRoot);
			// Update the root in our cache.
			followIndex.updateFollowee(_publicKey, indexRoot, System.currentTimeMillis());
		}
	}


	private void _updateCachedRecords(RemoteActions remote, HighLevelCache cache, FollowIndex followIndex, StreamRecords oldRecords, StreamRecords newRecords) throws IOException
	{
		// Note that we always cache the CIDs of the records, whether or not we cache the leaf data files within (since these record elements are tiny).
		Set<String> removeCids = new HashSet<String>();
		Set<String> addCids = new HashSet<String>();
		removeCids.addAll(oldRecords.getRecord());
		for (String cid : newRecords.getRecord())
		{
			// Remove this from the set of CIDs to remove.
			boolean didRemove = removeCids.remove(cid);
			if (!didRemove)
			{
				// If we didn't remove it, that means it is something new.
				addCids.add(cid);
			}
		}
		// For now, we just add the record CIDs, not the leaf elements.
		// TODO:  Fetch the leaf elements and apply the correct decay algorithm to expire cache elements.
		for (String rawCid : removeCids)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(rawCid);
			// TODO: Make this population use the filter and cache algorithm.
			StreamRecord record = GlobalData.deserializeRecord(remote.readData(cid));
			for (DataElement elt : record.getElements().getElement())
			{
				IpfsFile eltCid = IpfsFile.fromIpfsCid(elt.getCid());
				cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.FILE, eltCid);
			}
			
			cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, cid);
		}
		for (String rawCid : addCids)
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
