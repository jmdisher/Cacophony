package com.jeffdisher.cacophony.data.local.v2;

import java.io.Serializable;

import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * Helpers for decoding miscellaneous opcodes from the storage stream.
 * This is meant to be the general grab-bag of decoder helpers which don't have a more specific purpose.
 */
public interface IMiscUses
{
	void createConfig(String ipfsHost, String keyName);

	void setLastPublishedIndex(IpfsFile lastPublishedIndex);

	void setPinnedCount(IpfsFile cid, int count);

	void setPrefsKey(String keyName, Serializable value);
}
