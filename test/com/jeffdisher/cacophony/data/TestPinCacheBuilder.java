package com.jeffdisher.cacophony.data;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockNodeHelpers;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestPinCacheBuilder
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] {3});

	@Test
	public void testEmpty() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		PinCacheBuilder builder = new PinCacheBuilder(network);
		PinCacheData data = builder.finish();
		Assert.assertTrue(data.snapshotPinnedSet().isEmpty());
		network.shutdown();
	}

	@Test
	public void testHomeAndOneFollower() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		
		// Create the home data with 1 post with one attachment.
		IpfsFile homeRoot = MockNodeHelpers.createAndPublishEmptyChannelWithDescription(node, MockKeys.K1, "Home", "pic".getBytes());
		IpfsFile homePost = MockNodeHelpers.storeStreamRecord(node, MockKeys.K1, "First post!", "thumb".getBytes(), null, 0, null);
		homeRoot = MockNodeHelpers.attachPostToUserAndPublish(node, MockKeys.K1, homeRoot, homePost);
		
		// Create the followee data with 1 post with one attachment.
		IpfsFile followeeRoot = MockNodeHelpers.createAndPublishEmptyChannelWithDescription(node, MockKeys.K2, "Followee", "pic".getBytes());
		IpfsFile followeePost = MockNodeHelpers.storeStreamRecord(node, MockKeys.K2, "followee post", "next image".getBytes(), null, 0, null);
		followeeRoot = MockNodeHelpers.attachPostToUserAndPublish(node, MockKeys.K2, followeeRoot, followeePost);
		
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		PinCacheBuilder builder = new PinCacheBuilder(network);
		builder.addHomeUser(homeRoot);
		builder.addFollowee(followeeRoot, Collections.singletonMap(followeePost, new FollowingCacheElement(followeePost, F3, null, 1L)));
		PinCacheData data = builder.finish();
		
		// We expect to see the following:
		// "pic" (x2)
		// recommendations (x2)
		// "thumb"
		// "next image"
		// home post
		// follow post
		// home description
		// follow description
		// home streams
		// follow streams
		// home index
		// follow index
		Assert.assertEquals(12, data.snapshotPinnedSet().size());
		
		// Verify the double-counts.
		IpfsFile picCid = MockSingleNode.generateHash("pic".getBytes());
		data.delRef(picCid);
		data.delRef(picCid);
		
		IpfsFile emptyRecommendations = MockSingleNode.generateHash(GlobalData.serializeRecommendations(new StreamRecommendations()));
		data.delRef(emptyRecommendations);
		data.delRef(emptyRecommendations);
		Assert.assertEquals(10, data.snapshotPinnedSet().size());
		network.shutdown();
	}
}
