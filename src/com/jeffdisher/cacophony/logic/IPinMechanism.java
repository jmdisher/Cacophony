package com.jeffdisher.cacophony.logic;

import java.io.IOException;

import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * An abstraction over the ability to pin/unpin files on the attached IPFS node in order to facilitate testing.
 */
public interface IPinMechanism
{
	void add(IpfsFile cid) throws IOException;

	void rm(IpfsFile cid) throws IOException;
}