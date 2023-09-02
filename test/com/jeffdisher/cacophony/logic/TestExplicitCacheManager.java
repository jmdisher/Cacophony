package com.jeffdisher.cacophony.logic;

import java.util.concurrent.CyclicBarrier;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FutureVoid;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockNodeHelpers;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;


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
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		ExplicitCacheData.UserInfo info = manager.loadUserInfo(MockKeys.K0).get();
		Assert.assertEquals(MockSingleNode.generateHash("pic".getBytes()), info.userPicCid());
		// While we pin all for elements (index, recommendations, records, description, picture), we don't actually load the picture.
		Assert.assertEquals(4, node.loadCalls);
		// We see size checks come from 2 different locations:
		// ForeignChannelReader: (4) index, description, recommendations, records
		// ExplicitCacheLogic: (5) userpic, index, recommendations, records, description
		Assert.assertEquals(9, node.sizeCalls);
		
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
		int startPin = node.pinCalls;
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
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
		int endPin = node.pinCalls;
		Assert.assertEquals(startPin, endPin);
		// We failed to resolve so we shouldn't read anything.
		Assert.assertEquals(0, node.sizeCalls);
		Assert.assertEquals(0, node.loadCalls);
		
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
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		CachedRecordInfo info = manager.loadRecord(cid, true).get();
		Assert.assertEquals(MockSingleNode.generateHash("pic".getBytes()), info.thumbnailCid());
		
		manager.shutdown();
		network.shutdown();
	}

	@Test
	public void missingPostData() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, network);
		
		// Test that we fail to read the post.
		int startPin = node.pinCalls;
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		boolean didLoad;
		try
		{
			manager.loadRecord(MockSingleNode.generateHash("bogus".getBytes()), true).get();
			didLoad = true;
		}
		catch (IpfsConnectionException e)
		{
			didLoad = false;
		}
		Assert.assertFalse(didLoad);
		int endPin = node.pinCalls;
		Assert.assertEquals(startPin, endPin);
		// We directly check the record and see the failure in the size call, before the load call.
		Assert.assertEquals(0, node.loadCalls);
		Assert.assertEquals(1, node.sizeCalls);
		
		manager.shutdown();
		network.shutdown();
	}

	@Test
	public void existingPostData() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, network);
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		
		// Define a post.
		IpfsFile cid = MockNodeHelpers.storeStreamRecord(node, MockKeys.K0, "title", "pic".getBytes(), null, 0, null);
		
		// Verify that the cache is empty.
		Assert.assertEquals(0L, manager.getExplicitCacheSize());
		
		// Test that we fail to find it already existing in cache.
		Assert.assertNull(manager.getExistingRecord(cid));
		
		// Test that we can read the post.
		CachedRecordInfo info = manager.loadRecord(cid, true).get();
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
		MockSwarm swarm = new MockSwarm();
		MockSingleNode upstream = new MockSingleNode(swarm);
		MockSingleNode node = new MockSingleNode(swarm);
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, network);
		
		// Define a user.
		MockNodeHelpers.createAndPublishEmptyChannelWithDescription(upstream, MockKeys.K0, "user", "pic".getBytes());
		
		// Define a post.
		IpfsFile cid = MockNodeHelpers.storeStreamRecord(upstream, MockKeys.K0, "title", "pic".getBytes(), null, 0, null);
		
		// Test that we can read the user and post in an async manager.
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, true);
		ExplicitCacheData.UserInfo user = manager.loadUserInfo(MockKeys.K0).get();
		Assert.assertEquals(MockSingleNode.generateHash("pic".getBytes()), user.userPicCid());
		CachedRecordInfo post = manager.loadRecord(cid, true).get();
		Assert.assertEquals(MockSingleNode.generateHash("pic".getBytes()), post.thumbnailCid());
		
		manager.shutdown();
		network.shutdown();
	}

	@Test
	public void missingUserPic() throws Throwable
	{
		// Make sure that missing data causes this to fail and leave the pin counts unchanged.
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		byte[] userPic = "userPic".getBytes();
		MockNodeHelpers.createAndPublishEmptyChannelWithDescription(upstream, MockKeys.K0, "name", userPic);
		upstream.rm(MockSingleNode.generateHash(userPic));
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		
		int startPin = node.pinCalls;
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		boolean didLoad;
		try
		{
			manager.loadUserInfo(MockKeys.K0).get();
			didLoad = true;
		}
		catch (IpfsConnectionException e)
		{
			// This will manifest as a timeout checking the size of the pic.
			didLoad = false;
		}
		Assert.assertFalse(didLoad);
		int endPin = node.pinCalls;
		Assert.assertEquals(startPin, endPin);
		// While we pin all for elements (index, recommendations, records, description, picture), we don't actually load the picture.
		Assert.assertEquals(4, node.loadCalls);
		// We see size checks come from 2 different locations:
		// ForeignChannelReader: (4) index, description, recommendations, records
		// ExplicitCacheLogic: (1) userpic (we fail before getting on to the other elements)
		Assert.assertEquals(5, node.sizeCalls);
		
		manager.shutdown();
		scheduler.shutdown();
	}

	@Test
	public void missingRecommendations() throws Throwable
	{
		// Make sure that missing data causes this to fail and leave the pin counts unchanged.
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		MockNodeHelpers.createAndPublishEmptyChannelWithDescription(upstream, MockKeys.K0, "name", "userPic".getBytes());
		upstream.rm(MockSingleNode.generateHash(GlobalData.serializeRecommendations(new StreamRecommendations())));
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		
		int startPin = node.pinCalls;
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		boolean didLoad;
		try
		{
			manager.loadUserInfo(MockKeys.K0).get();
			didLoad = true;
		}
		catch (IpfsConnectionException e)
		{
			// This will show up as a timeout in looking for the size of the recommendations CID.
			didLoad = false;
		}
		Assert.assertFalse(didLoad);
		int endPin = node.pinCalls;
		Assert.assertEquals(startPin, endPin);
		// We see 2 load attempts:  index, description.  Then, we fail on the recommendations size check.
		Assert.assertEquals(2, node.loadCalls);
		// ForeignChannelReader: (3) index, description, recommendations
		Assert.assertEquals(3, node.sizeCalls);
		
		manager.shutdown();
		scheduler.shutdown();
	}

	@Test
	public void noLeafRecord() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		IpfsFile cid = MockNodeHelpers.storeStreamRecord(upstream, MockKeys.K1, "name", null, null, 0, null);
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		
		CachedRecordInfo record = manager.loadRecord(cid, true).get();
		Assert.assertNotNull(record);
		// We check the size of the record before load and then when building total.
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(2, node.sizeCalls);
		// A second attempt should be a cache hit and not touch the network.
		CachedRecordInfo record2 = manager.loadRecord(cid, true).get();
		Assert.assertNotNull(record2);
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(2, node.sizeCalls);
		
		manager.shutdown();
		scheduler.shutdown();
	}

	@Test
	public void videoRecord() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		IpfsFile cid = MockNodeHelpers.storeStreamRecord(upstream, MockKeys.K1, "name", "thumb".getBytes(), "video".getBytes(), 10, null);
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		
		CachedRecordInfo record = manager.loadRecord(cid, true).get();
		Assert.assertNotNull(record);
		// We check the record size, load it, then total record, thumbnail, and video.
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(4, node.sizeCalls);
		// A second attempt should be a cache hit and not touch the network.
		CachedRecordInfo record2 = manager.loadRecord(cid, true).get();
		Assert.assertNotNull(record2);
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(4, node.sizeCalls);
		
		manager.shutdown();
		scheduler.shutdown();
	}

	@Test
	public void audioRecord() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		IpfsFile cid = MockNodeHelpers.storeStreamRecord(upstream, MockKeys.K1, "name", null, null, 0, "audio".getBytes());
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		
		CachedRecordInfo record = manager.loadRecord(cid, true).get();
		Assert.assertNotNull(record);
		// We check the record size, load it, then total record and audio.
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(3, node.sizeCalls);
		// A second attempt should be a cache hit and not touch the network.
		CachedRecordInfo record2 = manager.loadRecord(cid, true).get();
		Assert.assertNotNull(record2);
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(3, node.sizeCalls);
		
		manager.shutdown();
		scheduler.shutdown();
	}

	@Test
	public void brokenRecordLeaf() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		// Break the video leaf and make sure we fail and don't change pin counts.
		byte[] videoData = "video".getBytes();
		IpfsFile cid = MockNodeHelpers.storeStreamRecord(upstream, MockKeys.K1, "name", "thumb".getBytes(), videoData, 10, null);
		upstream.rm(MockSingleNode.generateHash(videoData));
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		
		int startPin = node.getStoredFileSet().size();
		boolean didFail;
		try
		{
			manager.loadRecord(cid, true).get();
			didFail = false;
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		int endPin = node.getStoredFileSet().size();
		Assert.assertEquals(startPin, endPin);
		// We should see that pin calls were made, but were reverted.
		Assert.assertEquals(3, node.pinCalls);
		// We check the record size, load it, then total record, thumb, and the video pin fails before we check it.
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(3, node.sizeCalls);
		
		manager.shutdown();
		scheduler.shutdown();
	}

	@Test
	public void existingRecord() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		IpfsFile cid = MockNodeHelpers.storeStreamRecord(upstream, MockKeys.K1, "name", null, null, 0, null);
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		
		Assert.assertNull(manager.getExistingRecord(cid));
		CachedRecordInfo record = manager.loadRecord(cid, true).get();
		Assert.assertNotNull(record);
		Assert.assertTrue(record == manager.getExistingRecord(cid));
		
		manager.shutdown();
		scheduler.shutdown();
	}

	@Test
	public void concurrentUser() throws Throwable
	{
		// We want to start multiple threads, operating on the same data store (acting as concurrent requests on the
		// same running server) and have them all perform the same request, verifying that the final state has the same
		// state cached on the local node.
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		MockNodeHelpers.createAndPublishEmptyChannelWithDescription(upstream, MockKeys.K1, "name", "userPic".getBytes());
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		
		Thread[] threads = new Thread[10];
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i] = new Thread(() -> {
				ExplicitCacheData.UserInfo userInfo;
				try
				{
					userInfo = manager.loadUserInfo(MockKeys.K1).get();
				}
				catch (ProtocolDataException e)
				{
					// Not expected.
					throw new AssertionError(e);
				}
				catch (IpfsConnectionException e)
				{
					// Not expected.
					throw new AssertionError(e);
				}
				catch (KeyException e)
				{
					// Not expected.
					throw new AssertionError(e);
				}
				Assert.assertNotNull(userInfo);
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
		// While we pin all 5 elements (index, recommendations, records, description, picture), we don't actually load the picture.
		Assert.assertEquals(5, node.getStoredFileSet().size());
		
		// This test is somewhat racy so we know that we will see between 1 and 10 load attempts (usually 10), but we expect each attempt to have a consistent multiple.
		Assert.assertTrue(node.loadCalls >= 3);
		Assert.assertTrue(node.loadCalls <= (3 * threads.length));
		// We see size checks come from 2 different locations:
		// ForeignChannelReader: (4) index, description, recommendations, records
		// ExplicitCacheLogic: (5) userpic, index, recommendations, records, description
		Assert.assertTrue(node.sizeCalls >= 9);
		Assert.assertTrue(node.sizeCalls <= (9 * threads.length));
		int multiple = node.loadCalls / 3;
		Assert.assertEquals(9 * multiple, node.sizeCalls);
		
		manager.shutdown();
		scheduler.shutdown();
	}

	@Test
	public void concurrentRecord() throws Throwable
	{
		// We want to start multiple threads, operating on the same data store (acting as concurrent requests on the
		// same running server) and have them all perform the same request, verifying that the final state has the same
		// state cached on the local node.
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		IpfsFile cid = MockNodeHelpers.storeStreamRecord(upstream, MockKeys.K1, "name", "thumb".getBytes(), "video".getBytes(), 10, null);
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		
		Thread[] threads = new Thread[10];
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i] = new Thread(() -> {
				CachedRecordInfo record;
				try
				{
					record = manager.loadRecord(cid, true).get();
				}
				catch (ProtocolDataException e)
				{
					// Not expected.
					throw new AssertionError(e);
				}
				catch (IpfsConnectionException e)
				{
					// Not expected.
					throw new AssertionError(e);
				}
				Assert.assertNotNull(record);
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
		// We check the record size, load it, then total record, thumbnail, and video.
		// We know that this results in 3 pins (the record, the thumbnail, and video):
		Assert.assertEquals(3, node.getStoredFileSet().size());
		// We should see up to 10x the usual count, since each thread does the same thing, even if only 1 writes-back.
		// (this will usually be 10x but can technically be as low as 1x, but we expect the right multiples).
		Assert.assertTrue(node.loadCalls >= 1);
		Assert.assertTrue(node.loadCalls <= threads.length);
		Assert.assertTrue(node.sizeCalls >= 4);
		Assert.assertTrue(node.sizeCalls <= (4 * threads.length));
		// We expect X * 1 for loads, so that is our multiple.
		Assert.assertEquals(node.sizeCalls, 4 * node.loadCalls);
		
		manager.shutdown();
		scheduler.shutdown();
	}

	@Test
	public void purge() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		IpfsFile cid = MockNodeHelpers.storeStreamRecord(upstream, MockKeys.K1, "name", null, null, 0, null);
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, false);
		
		// Populate the cache.
		CachedRecordInfo record = manager.loadRecord(cid, true).get();
		Assert.assertNotNull(record);
		
		// Check the size.
		long size = manager.getExplicitCacheSize();
		Assert.assertEquals(377L, size);
		
		// Purge.
		manager.purgeCacheFullyAndGc().get();
		
		// Check the cache.
		Assert.assertNull(manager.getExistingRecord(cid));
		
		// Check the size.
		size = manager.getExplicitCacheSize();
		Assert.assertEquals(0L, size);
		
		manager.shutdown();
		scheduler.shutdown();
	}

	@Test
	public void uniqueAndBackground() throws Throwable
	{
		// Check what happens when we issue lots of requests to a small set of records or users, in an asynchronous manager, some which pass and some which fail.
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		MockNodeHelpers.createAndPublishEmptyChannelWithDescription(upstream, MockKeys.K0, "user0", "userPic".getBytes());
		IpfsFile cid0 = MockNodeHelpers.storeStreamRecord(upstream, MockKeys.K0, "name", "thumb".getBytes(), "video".getBytes(), 10, null);
		MockNodeHelpers.createAndPublishEmptyChannelWithDescription(upstream, MockKeys.K1, "user1", "other user".getBytes());
		IpfsFile cid1 = MockNodeHelpers.storeStreamRecord(upstream, MockKeys.K1, "other post", null, null, 0, null);
		IpfsFile bogusRecord = MockSingleNode.generateHash("bogus stream reference".getBytes());
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, true);
		
		// We want to install a barrier in the upstream node, waiting on us and the single scheduler thread, so we can queue up multiple requests and see them de-duplicate.
		CyclicBarrier barrier = new CyclicBarrier(2);
		upstream.installSingleUseBarrier(barrier);
		// We will make three calls for each of the relevant data elements, we aren't sure when the background thread will pick them up but at least 0 == 1 or 1 == 2 (or both).
		ExplicitCacheManager.FutureRecord[] records0 = new ExplicitCacheManager.FutureRecord[3];
		ExplicitCacheManager.FutureRecord[] records1 = new ExplicitCacheManager.FutureRecord[3];
		ExplicitCacheManager.FutureRecord[] records2 = new ExplicitCacheManager.FutureRecord[3];
		ExplicitCacheManager.FutureUserInfo[] users0 = new ExplicitCacheManager.FutureUserInfo[3];
		ExplicitCacheManager.FutureUserInfo[] users1 = new ExplicitCacheManager.FutureUserInfo[3];
		ExplicitCacheManager.FutureUserInfo[] users2 = new ExplicitCacheManager.FutureUserInfo[3];
		FutureVoid[] gcs = new FutureVoid[3];
		for (int i = 0; i < 3; ++i)
		{
			records0[i] = manager.loadRecord(cid0, true);
			records1[i] = manager.loadRecord(cid1, true);
			records2[i] = manager.loadRecord(bogusRecord, true);
			users0[i] = manager.loadUserInfo(MockKeys.K0);
			users1[i] = manager.loadUserInfo(MockKeys.K1);
			users2[i] = manager.loadUserInfo(MockKeys.K2);
			gcs[i] = manager.purgeCacheFullyAndGc();
		}
		// Let the execution proceed and verify that at least 
		barrier.await();
		Assert.assertTrue((records0[0] == records0[1]) || (records0[1] == records0[2]));
		Assert.assertTrue((records1[0] == records1[1]) || (records1[1] == records1[2]));
		Assert.assertTrue((records2[0] == records2[1]) || (records2[1] == records2[2]));
		Assert.assertTrue((users0[0] == users0[1]) || (users0[1] == users0[2]));
		Assert.assertTrue((users1[0] == users1[1]) || (users1[1] == users1[2]));
		Assert.assertTrue((users2[0] == users2[1]) || (users2[1] == users2[2]));
		Assert.assertTrue((gcs[0] == gcs[1]) || (gcs[1] == gcs[2]));
		
		// Now, wait for everything to finish and make sure we see the expected results.
		// Check the hits.
		for (int i = 0; i < 3; ++i)
		{
			records0[i].get();
			records1[i].get();
			users0[i].get();
			users1[i].get();
			gcs[i].get();
		}
		// Check the misses.
		for (int i = 0; i < 3; ++i)
		{
			try
			{
				records2[i].get();
				Assert.fail();
			}
			catch (IpfsConnectionException e)
			{
				// Expected.
			}
			try
			{
				users2[i].get();
				Assert.fail();
			}
			catch (KeyException e)
			{
				// Expected.
			}
		}
		
		manager.shutdown();
		scheduler.shutdown();
	}

	@Test
	public void userInfoRefresh() throws Throwable
	{
		// We want to observe the background refresh of user info in the explicit cache.
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		MockNodeHelpers.createAndPublishEmptyChannelWithDescription(upstream, MockKeys.K0, "user0", "userPic".getBytes());
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, true);
		
		// We do the initial look-up to prime the cache.
		ExplicitCacheData.UserInfo startInfo = manager.loadUserInfo(MockKeys.K0).get();
		IpfsFile userIndex = startInfo.indexCid();
		// We expect 4 loads:  ForeignChannelReader will do size+load for index, records, recommendations, description.
		// We expect 9 size checks:  ForeignChannelReader does this for meta-data, the pic, and then does the meta-data after pinning.
		// We expect 5 pins:  All meta-data and the pic.
		Assert.assertEquals(4, node.loadCalls);
		Assert.assertEquals(9, node.sizeCalls);
		Assert.assertEquals(5, node.pinCalls);
		
		// We now set the refresh threshold to something very low.
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			PrefsData prefs = access.readPrefs();
			prefs.explicitUserInfoRefreshMillis = 1L;
			access.writePrefs(prefs);
		}
		
		// Change the user info.
		MockNodeHelpers.updateAndPublishChannelDescription(upstream, MockKeys.K0, "updated name", "updated pic".getBytes());
		
		// Install a barrier on the read, so we can verify that first refresh returns immediately while the other is progressing.
		CyclicBarrier barrier = new CyclicBarrier(2);
		upstream.installSingleUseBarrier(barrier);
		
		// Do the first read, verify it is unchanged, and then access the barrier to verify we have synchronized with the refresh.
		ExplicitCacheData.UserInfo lateInfo = manager.loadUserInfo(MockKeys.K0).get();
		barrier.await();
		
		// Now, spin on the refresh until we see the change (since it is asynchronous but complete soon).
		int count = 0;
		while (userIndex.equals(lateInfo.indexCid()))
		{
			Thread.sleep(10L);
			lateInfo = manager.loadUserInfo(MockKeys.K0).get();
			count += 1;
		}
		// We should enter this at least once since the first refresh is returned while the update happens.
		Assert.assertTrue(count > 0);
		// We expect the call counts to be mostly doubled from the above, since the refresh is run twice.  Pins are coalesced, though, so only the 3 new elements are pinned.
		Assert.assertEquals(8, node.loadCalls);
		Assert.assertEquals(18, node.sizeCalls);
		Assert.assertEquals(8, node.pinCalls);
		
		manager.shutdown();
		scheduler.shutdown();
	}

	@Test
	public void requestUpgrade() throws Throwable
	{
		// We want to send in multiple record meta-data requests, block the call, then add a full request, to make sure that they are all completed.
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		IpfsFile cid0 = MockNodeHelpers.storeStreamRecord(upstream, MockKeys.K0, "name", "thumb".getBytes(), "video".getBytes(), 10, null);
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = MockNodeHelpers.createWallClockContext(node, scheduler);
		ExplicitCacheManager manager = new ExplicitCacheManager(context.accessTuple, context.logger, context.currentTimeMillisGenerator, true);
		
		// Make one request to verify that it thinks there is more data.
		CachedRecordInfo start = manager.loadRecord(cid0, false).get();
		Assert.assertEquals(777, start.combinedSizeBytes());
		Assert.assertTrue(start.hasDataToCache());
		Assert.assertNull(start.thumbnailCid());
		Assert.assertNull(start.videoCid());
		Assert.assertNull(start.audioCid());
		
		// We want to install a barrier in the upstream node, waiting on us and the single scheduler thread, so we can queue up multiple requests to force the flow-through upgrade.
		CyclicBarrier barrier = new CyclicBarrier(2);
		upstream.installSingleUseBarrier(barrier);
		
		// Make multiple requests for the meta-data, so that they de-duplicate and have a chance to be picked up.
		ExplicitCacheManager.FutureRecord r0 = manager.loadRecord(cid0, false);
		ExplicitCacheManager.FutureRecord r1 = manager.loadRecord(cid0, false);
		
		// Then add a request for full data and unblock the barrier.
		ExplicitCacheManager.FutureRecord r2 = manager.loadRecord(cid0, true);
		barrier.await();
		
		// Wait on all to complete.
		r0.get();
		r1.get();
		r2.get();
		
		// Make a final non-force request and make sure it still has all the data.
		CachedRecordInfo end = manager.loadRecord(cid0, false).get();
		Assert.assertEquals(787, end.combinedSizeBytes());
		Assert.assertFalse(end.hasDataToCache());
		Assert.assertEquals(MockSingleNode.generateHash("thumb".getBytes()), end.thumbnailCid());
		Assert.assertEquals(MockSingleNode.generateHash("video".getBytes()), end.videoCid());
		Assert.assertNull(start.audioCid());
		
		manager.shutdown();
		scheduler.shutdown();
	}
}
