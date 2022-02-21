package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestListChannelEntriesCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testWithAndWithoutKey() throws IOException
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY, null);
		
		// We need to create the channel first so we will just use the command to do that.
		user1.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		// Check that we can list entries with null key.
		user1.runCommand(null, new ListChannelEntriesCommand(null));
		
		// Check that we can list entries with an explicit key.
		user1.runCommand(null, new ListChannelEntriesCommand(PUBLIC_KEY));
	}
}
