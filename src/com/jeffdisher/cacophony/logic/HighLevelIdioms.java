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

	public static StreamIndex readIndexForKey(RemoteActions remote, IpfsKey keyToResolve) throws IOException
	{
		IpfsFile indexHash = remote.resolvePublicKey(keyToResolve);
		byte[] rawIndex = remote.readData(indexHash);
		return GlobalData.deserializeIndex(rawIndex);
	}

	public static IpfsFile saveAndPublishIndex(Executor executor, RemoteActions remote, StreamIndex streamIndex) throws IOException
	{
		return _saveAndPublishIndex(executor, remote, streamIndex);
	}

	public static IpfsFile saveAndPublishNewIndex(Executor executor, RemoteActions remote, IpfsFile description, IpfsFile recommendations, IpfsFile records) throws IOException
	{
		StreamIndex streamIndex = new StreamIndex();
		streamIndex.setVersion(1);
		streamIndex.setDescription(description.cid().toBase58());
		streamIndex.setRecommendations(recommendations.cid().toBase58());
		streamIndex.setRecords(records.cid().toBase58());
		return _saveAndPublishIndex(executor, remote, streamIndex);
	}


	private static IpfsFile _saveData(Executor executor, RemoteActions remote, byte[] data)
	{
		try
		{
			return remote.saveData(data);
		}
		catch (IOException e)
		{
			executor.fatalError(e);
			// TODO:  Determine how to handle this failure path - probably a runtime exception to break us out to the top.
			throw Assert.unexpected(e);
		}
	}

	private static IpfsFile _saveAndPublishIndex(Executor executor, RemoteActions remote, StreamIndex streamIndex) throws IOException
	{
		byte[] rawIndex = GlobalData.serializeIndex(streamIndex);
		IpfsFile hashIndex = _saveData(executor, remote, rawIndex);
		Assert.assertTrue(null != hashIndex);
		remote.publishIndex(hashIndex);
		return hashIndex;
	}
}
