package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;


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
		IpfsFile homePost = _makePost(node, "First post!", F2);
		IpfsFile homeRoot = _createUser(node, "Home", F1, homePost);
		
		// Create the followee data with 1 post with one attachment.
		IpfsFile followeePost = _makePost(node, "followee post", F3);
		IpfsFile followeeRoot = _createUser(node, "Followee", F1, followeePost);
		
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		PinCacheBuilder builder = new PinCacheBuilder(network);
		builder.addHomeUser(homeRoot);
		builder.addFollowee(followeeRoot, Collections.singletonMap(followeePost, new FollowingCacheElement(followeePost, F3, null, 1L)));
		PinCacheData data = builder.finish();
		
		// We expect to see the following:
		// F1 (x2)
		// recommendations (x2)
		// F2
		// F3
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
		data.delRef(F1);
		data.delRef(F1);
		
		IpfsFile emptyRecommendations = MockSingleNode.generateHash(GlobalData.serializeRecommendations(new StreamRecommendations()));
		data.delRef(emptyRecommendations);
		data.delRef(emptyRecommendations);
		Assert.assertEquals(10, data.snapshotPinnedSet().size());
		network.shutdown();
	}


	private IpfsFile _createUser(MockSingleNode node, String name, IpfsFile pic, IpfsFile post) throws SizeConstraintException
	{
		StreamDescription description = new StreamDescription();
		description.setName(name);
		description.setDescription("Description forthcoming");
		description.setPicture(pic.toSafeString());
		
		StreamRecommendations recommendations = new StreamRecommendations();
		
		StreamRecords records = new StreamRecords();
		records.getRecord().add(post.toSafeString());
		
		StreamIndex streamIndex = new StreamIndex();
		streamIndex.setVersion(1);
		streamIndex.setDescription(_storeData(node, GlobalData.serializeDescription(description)).toSafeString());
		streamIndex.setRecommendations(_storeData(node, GlobalData.serializeRecommendations(recommendations)).toSafeString());
		streamIndex.setRecords(_storeData(node, GlobalData.serializeRecords(records)).toSafeString());
		
		return _storeData(node, GlobalData.serializeIndex(streamIndex));
	}

	private IpfsFile _makePost(MockSingleNode node, String title, IpfsFile image) throws SizeConstraintException
	{
		StreamRecord record = new StreamRecord();
		record.setName(title);
		record.setDescription("desc");
		record.setPublishedSecondsUtc(1L);
		record.setPublisherKey(MockKeys.K1.toPublicKey());
		DataArray elements = new DataArray();
		if (null != image)
		{
			DataElement element = new DataElement();
			element.setCid(image.toSafeString());
			element.setMime("image/jpeg");
			element.setSpecial(ElementSpecialType.IMAGE);
			elements.getElement().add(element);
		}
		record.setElements(elements);
		return _storeData(node, GlobalData.serializeRecord(record));
	}

	private IpfsFile _storeData(MockSingleNode node, byte[] data)
	{
		return node.storeData(new ByteArrayInputStream(data));
	}
}
