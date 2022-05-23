package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * A collection of static helpers for common high-level operations.
 */
public class HighLevelIdioms
{
	public static IpfsFile saveAndPublishIndex(RemoteActions remote, LocalConfig local, StreamIndex streamIndex) throws IpfsConnectionException
	{
		IpfsFile hashIndex = CommandHelpers.serializeSaveAndPublishIndex(remote, streamIndex);
		// Update the local index.
		LocalIndex localIndex = local.readLocalIndex();
		local.storeSharedIndex(new LocalIndex(localIndex.ipfsHost(), localIndex.keyName(), hashIndex));
		// Return the final hash.
		return hashIndex;
	}
}
