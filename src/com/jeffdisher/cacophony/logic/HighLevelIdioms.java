package com.jeffdisher.cacophony.logic;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A collection of static helpers for common high-level operations.
 */
public class HighLevelIdioms
{
	public static IpfsFile saveData(Executor executor, RemoteActions remote, byte[] data)
	{
		return _saveData(executor, remote, data);
	}

	public static StreamIndex readIndexForKey(RemoteActions remote, IpfsKey keyToResolve, IpfsFile[] outFile) throws IOException
	{
		IpfsFile indexHash = remote.resolvePublicKey(keyToResolve);
		if (null != outFile)
		{
			outFile[0] = indexHash;
		}
		byte[] rawIndex = remote.readData(indexHash);
		return GlobalData.deserializeIndex(rawIndex);
	}

	public static IpfsFile saveAndPublishIndex(Executor executor, RemoteActions remote, ILocalActions local, StreamIndex streamIndex) throws IOException
	{
		return _saveAndPublishIndex(executor, remote, local, streamIndex);
	}

	public static IpfsFile saveAndPublishNewIndex(Executor executor, RemoteActions remote, ILocalActions local, IpfsFile description, IpfsFile recommendations, IpfsFile records) throws IOException
	{
		StreamIndex streamIndex = new StreamIndex();
		streamIndex.setVersion(1);
		streamIndex.setDescription(description.toSafeString());
		streamIndex.setRecommendations(recommendations.toSafeString());
		streamIndex.setRecords(records.toSafeString());
		return _saveAndPublishIndex(executor, remote, local, streamIndex);
	}


	private static IpfsFile _saveData(Executor executor, RemoteActions remote, byte[] data)
	{
		try
		{
			IpfsFile uploaded = remote.saveData(data);
			// TODO:  Remove this check once we have confirmed that this is working as expected.
			Assert.assertTrue(data.length == (int)remote.getSizeInBytes(uploaded));
			return uploaded;
		}
		catch (IOException e)
		{
			executor.fatalError(e);
			// TODO:  Determine how to handle this failure path - probably a runtime exception to break us out to the top.
			throw Assert.unexpected(e);
		}
	}

	private static IpfsFile _saveAndPublishIndex(Executor executor, RemoteActions remote, ILocalActions local, StreamIndex streamIndex) throws IOException
	{
		// Serialize the index file.
		byte[] rawIndex = GlobalData.serializeIndex(streamIndex);
		// Save it to the IPFS node.
		IpfsFile hashIndex = _saveData(executor, remote, rawIndex);
		Assert.assertTrue(null != hashIndex);
		// Publish it to IPNS.
		remote.publishIndex(hashIndex);
		// Return the final hash.
		return hashIndex;
	}
}
