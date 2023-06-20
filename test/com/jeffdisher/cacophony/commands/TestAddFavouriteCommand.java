package com.jeffdisher.cacophony.commands;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;


public class TestAddFavouriteCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";

	@Test
	public void basicUsage() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode remoteUser = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode localUser = new MockUserNode(KEY_NAME, null, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// Create a user and make a post.
		IpfsFile post = _createUserWithPost(remoteUser);
		
		// Favourite that post on the other side.
		AddFavouriteCommand addFavourite = new AddFavouriteCommand(post);
		localUser.runCommand(null, addFavourite);
		
		// Verify that adding it again fails.
		boolean didFail = false;
		try
		{
			localUser.runCommand(null, addFavourite);
		}
		catch (UsageException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
	}

	@Test
	public void missingPost() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode localUser = new MockUserNode(KEY_NAME, null, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// Check that this fails if not found.
		AddFavouriteCommand addFavourite = new AddFavouriteCommand(MockSingleNode.generateHash(new byte[] { 1 }));
		boolean didFail = false;
		try
		{
			localUser.runCommand(null, addFavourite);
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
	}

	@Test
	public void concurrentAdd() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode remoteUser = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode localUser = new MockUserNode(KEY_NAME, null, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// Create a user and make a post.
		IpfsFile post = _createUserWithPost(remoteUser);
		
		// Create multiple threads and have them all try to add this to verify that only one succeeds (as they race on the commit).
		Thread[] threads = new Thread[10];
		AtomicInteger failures = new AtomicInteger(0);
		// Force the node to be ready.
		localUser.getContext();
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i] = new Thread(()-> {
				AddFavouriteCommand addFavourite = new AddFavouriteCommand(post);
				try
				{
					localUser.runCommand(null, addFavourite);
				}
				catch (UsageException e)
				{
					// This should happen in all but one case.
					failures.incrementAndGet();
				}
				catch (Throwable e)
				{
					// Should not happen.
					Assert.fail(e.getLocalizedMessage());
				}
			});
		}
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i].start();
		}
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i].join();
		}
		Assert.assertEquals(threads.length - 1, failures.get());
	}


	private static IpfsFile _createUserWithPost(MockUserNode remoteUser) throws Throwable
	{
		CreateChannelCommand create = new CreateChannelCommand(KEY_NAME);
		ChangedRoot root = remoteUser.runCommand(null, create);
		Assert.assertNotNull(root);
		PublishCommand publish = new PublishCommand("post", "description", null, new ElementSubCommand[0]);
		OnePost post = remoteUser.runCommand(null, publish);
		return post.recordCid;
	}
}
