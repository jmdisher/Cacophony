package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A collection of static helpers for common high-level operations.
 */
public class HighLevelIdioms
{
	public static IpfsFile saveAndPublishIndex(RemoteActions remote, LocalConfig local, StreamIndex streamIndex) throws IpfsConnectionException
	{
		return _saveAndPublishIndex(remote, local, streamIndex);
	}

	public static IpfsFile saveAndPublishNewIndex(RemoteActions remote, LocalConfig local, IpfsFile description, IpfsFile recommendations, IpfsFile records) throws IpfsConnectionException
	{
		StreamIndex streamIndex = new StreamIndex();
		streamIndex.setVersion(1);
		streamIndex.setDescription(description.toSafeString());
		streamIndex.setRecommendations(recommendations.toSafeString());
		streamIndex.setRecords(records.toSafeString());
		return _saveAndPublishIndex(remote, local, streamIndex);
	}


	private static IpfsFile _saveAndPublishIndex(RemoteActions remote, LocalConfig local, StreamIndex streamIndex) throws IpfsConnectionException
	{
		// Serialize the index file.
		byte[] rawIndex = GlobalData.serializeIndex(streamIndex);
		// Save it to the IPFS node.
		IpfsFile hashIndex = remote.saveData(rawIndex);
		Assert.assertTrue(null != hashIndex);
		// Publish it to IPNS.
		boolean didPublish = remote.publishIndex(hashIndex);
		// TODO:  Remove this assertion once we can handle the error.
		Assert.assertTrue(didPublish);
		// Update the local index.
		LocalIndex localIndex = local.readLocalIndex();
		local.storeSharedIndex(new LocalIndex(localIndex.ipfsHost(), localIndex.keyName(), hashIndex));
		// Return the final hash.
		return hashIndex;
	}
}
