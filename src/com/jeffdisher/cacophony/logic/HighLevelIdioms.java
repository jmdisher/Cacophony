package com.jeffdisher.cacophony.logic;

import java.io.InputStream;

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
	public static IpfsFile saveData(RemoteActions remote, byte[] data) throws IpfsConnectionException
	{
		return remote.saveData(data);
	}

	public static IpfsFile saveStream(RemoteActions remote, InputStream stream) throws IpfsConnectionException
	{
		return remote.saveStream(stream);
	}

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
		remote.publishIndex(hashIndex);
		// Update the local index.
		LocalIndex localIndex = local.readLocalIndex();
		local.storeSharedIndex(new LocalIndex(localIndex.ipfsHost(), localIndex.keyName(), hashIndex));
		// Return the final hash.
		return hashIndex;
	}
}
