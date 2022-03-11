package com.jeffdisher.cacophony.logic;

import java.io.InputStream;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A collection of static helpers for common high-level operations.
 */
public class HighLevelIdioms
{
	public static IpfsFile saveData(Executor executor, RemoteActions remote, byte[] data) throws IpfsConnectionException
	{
		return _saveData(executor, remote, data);
	}

	public static IpfsFile saveStream(Executor executor, RemoteActions remote, InputStream stream) throws IpfsConnectionException
	{
		return remote.saveStream(stream);
	}

	public static IpfsFile saveAndPublishIndex(Executor executor, RemoteActions remote, ILocalActions local, StreamIndex streamIndex) throws IpfsConnectionException
	{
		return _saveAndPublishIndex(executor, remote, local, streamIndex);
	}

	public static IpfsFile saveAndPublishNewIndex(Executor executor, RemoteActions remote, ILocalActions local, IpfsFile description, IpfsFile recommendations, IpfsFile records) throws IpfsConnectionException
	{
		StreamIndex streamIndex = new StreamIndex();
		streamIndex.setVersion(1);
		streamIndex.setDescription(description.toSafeString());
		streamIndex.setRecommendations(recommendations.toSafeString());
		streamIndex.setRecords(records.toSafeString());
		return _saveAndPublishIndex(executor, remote, local, streamIndex);
	}


	private static IpfsFile _saveData(Executor executor, RemoteActions remote, byte[] data) throws IpfsConnectionException
	{
		IpfsFile uploaded = remote.saveData(data);
		// TODO:  Remove this check once we have confirmed that this is working as expected.
		Assert.assertTrue(data.length == (int)remote.getSizeInBytes(uploaded));
		return uploaded;
	}

	private static IpfsFile _saveAndPublishIndex(Executor executor, RemoteActions remote, ILocalActions local, StreamIndex streamIndex) throws IpfsConnectionException
	{
		// Serialize the index file.
		byte[] rawIndex = GlobalData.serializeIndex(streamIndex);
		// Save it to the IPFS node.
		IpfsFile hashIndex = _saveData(executor, remote, rawIndex);
		Assert.assertTrue(null != hashIndex);
		// Publish it to IPNS.
		remote.publishIndex(hashIndex);
		// Update the local index.
		LocalIndex localIndex = local.readIndex();
		local.storeIndex(new LocalIndex(localIndex.ipfsHost(), localIndex.keyName(), hashIndex));
		// Return the final hash.
		return hashIndex;
	}
}
