package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public class TestRefreshNextFolloweeCommand
{
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");
	private static final IpfsKey PUBLIC_KEY3 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo145");

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

	@Test
	public void testTwoFollowees() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, null);
		MockUserNode user3 = new MockUserNode(KEY_NAME, PUBLIC_KEY3, user2);
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, user3);
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		// We need to add the followees.
		user2.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		user3.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY3));
		
		// We should be able to run this multiple times, without it causing problems.
		FollowIndex followees = user.readFollowIndex();
		IpfsKey nextKey = followees.nextKeyToPoll();
		// We expect to do the initial check on the first one we added (since it was populated when first read).
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		user.runCommand(null, command);
		nextKey = user.readFollowIndex().nextKeyToPoll();
		// The key should have rotated, even though nothing changed.
		Assert.assertEquals(PUBLIC_KEY3, nextKey);
		user.runCommand(null, command);
		nextKey = followees.nextKeyToPoll();
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
	}
}
