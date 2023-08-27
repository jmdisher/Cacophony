package com.jeffdisher.cacophony.logic;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockNodeHelpers;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.KeyException;


public class TestExplicitCacheManager
{
	@Test
	public void userInfo() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, network);
		
		// Define a user.
		MockNodeHelpers.createAndPublishEmptyChannelWithDescription(node, MockKeys.K0, "user", "pic".getBytes());
		
		// Test that we can read the user.
		ExplicitCacheManager manager = new ExplicitCacheManager(context, false);
		ExplicitCacheData.UserInfo info = manager.loadUserInfo(MockKeys.K0).get();
		Assert.assertEquals(MockSingleNode.generateHash("pic".getBytes()), info.userPicCid());
		
		manager.shutdown();
		network.shutdown();
	}

	@Test
	public void missingUserInfo() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, network);
		
		// Test that we fail to find the user.
		ExplicitCacheManager manager = new ExplicitCacheManager(context, false);
		boolean didLoad;
		try
		{
			manager.loadUserInfo(MockKeys.K0).get();
			didLoad = true;
		}
		catch (KeyException e)
		{
			didLoad = false;
		}
		Assert.assertFalse(didLoad);
		
		manager.shutdown();
		network.shutdown();
	}

	@Test
	public void postData() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, network);
		
		// Define a post.
		IpfsFile cid = MockNodeHelpers.storeStreamRecord(node, MockKeys.K0, "title", "pic".getBytes(), null, 0, null);
		
		// Test that we can read the post.
		ExplicitCacheManager manager = new ExplicitCacheManager(context, false);
		CachedRecordInfo info = manager.loadRecord(cid).get();
		Assert.assertEquals(MockSingleNode.generateHash("pic".getBytes()), info.thumbnailCid());
		
		network.shutdown();
	}

	@Test
	public void missingPostData() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, network);
		
		// Test that we fail to read the post.
		ExplicitCacheManager manager = new ExplicitCacheManager(context, false);
		boolean didLoad;
		try
		{
			manager.loadRecord(MockSingleNode.generateHash("bogus".getBytes())).get();
			didLoad = true;
		}
		catch (IpfsConnectionException e)
		{
			didLoad = false;
		}
		Assert.assertFalse(didLoad);
		
		manager.shutdown();
		network.shutdown();
	}

	@Test
	public void existingPostData() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, network);
		ExplicitCacheManager manager = new ExplicitCacheManager(context, false);
		
		// Define a post.
		IpfsFile cid = MockNodeHelpers.storeStreamRecord(node, MockKeys.K0, "title", "pic".getBytes(), null, 0, null);
		
		// Verify that the cache is empty.
		Assert.assertEquals(0L, manager.getExplicitCacheSize());
		
		// Test that we fail to find it already existing in cache.
		Assert.assertNull(manager.getExistingRecord(cid));
		
		// Test that we can read the post.
		CachedRecordInfo info = manager.loadRecord(cid).get();
		Assert.assertEquals(MockSingleNode.generateHash("pic".getBytes()), info.thumbnailCid());
		
		// Verify that we now see it in cache.
		info = manager.getExistingRecord(cid);
		Assert.assertEquals(MockSingleNode.generateHash("pic".getBytes()), info.thumbnailCid());
		
		// Verify that the size has changed.
		Assert.assertEquals(576L, manager.getExplicitCacheSize());
		
		manager.shutdown();
		network.shutdown();
	}

	@Test
	public void asyncBasics() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, network);
		
		// Define a user.
		MockNodeHelpers.createAndPublishEmptyChannelWithDescription(node, MockKeys.K0, "user", "pic".getBytes());
		
		// Define a post.
		IpfsFile cid = MockNodeHelpers.storeStreamRecord(node, MockKeys.K0, "title", "pic".getBytes(), null, 0, null);
		
		// Test that we can read the user and post in an async manager.
		ExplicitCacheManager manager = new ExplicitCacheManager(context, true);
		ExplicitCacheData.UserInfo user = manager.loadUserInfo(MockKeys.K0).get();
		Assert.assertEquals(MockSingleNode.generateHash("pic".getBytes()), user.userPicCid());
		CachedRecordInfo post = manager.loadRecord(cid).get();
		Assert.assertEquals(MockSingleNode.generateHash("pic".getBytes()), post.thumbnailCid());
		
		manager.shutdown();
		network.shutdown();
	}
}
