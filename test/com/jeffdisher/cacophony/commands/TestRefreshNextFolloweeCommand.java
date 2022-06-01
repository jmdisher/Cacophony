package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public class TestRefreshNextFolloweeCommand
{
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");

	@Test
	public void testNoFolloweesException() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, null);
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		boolean didThrow = false;
		try
		{
			user.runCommand(null, command);
		}
		catch (UsageException e)
		{
			didThrow = true;
		}
		Assert.assertTrue(didThrow);
	}

	@Test
	public void testOneFollowee() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, null);
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, user1);
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		// We need to add a followee.
		user1.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		
		// We should be able to run this multiple times, without it causing problems.
		user.runCommand(null, command);
		user.runCommand(null, command);
	}
}
