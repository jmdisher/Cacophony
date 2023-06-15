package com.jeffdisher.cacophony.projection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v3.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v3.OpcodeContext;
import com.jeffdisher.cacophony.projection.ProjectionBuilder.Projections;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * A short-lived set of tests to demonstrate portability of the projections between V2 and V3 data models.
 */
public class TestV2V3
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] {3});

	@Test
	public void channelData() throws Throwable
	{
		ChannelData channelData = ChannelData.create();
		channelData.initializeChannelState("key", MockKeys.K1, F1);
		channelData.setLastPublishedIndex("key", MockKeys.K1, F2);
		byte[] v2 = _serializeV2(channelData, null, null);
		
		Tuple tuple2 = _deserializeV2(v2);
		Assert.assertEquals(1, tuple2.channelData.getKeyNames().size());
		Assert.assertEquals("key", tuple2.channelData.getKeyNames().iterator().next());
		Assert.assertEquals(F2, tuple2.channelData.getLastPublishedIndex("key"));
		byte[] v3 = _serializeV3(tuple2.channelData, tuple2.prefsData, tuple2.followeeData);
		
		Tuple tuple3 = _deserializeV3(v3);
		Assert.assertEquals(1, tuple3.channelData.getKeyNames().size());
		Assert.assertEquals("key", tuple3.channelData.getKeyNames().iterator().next());
		Assert.assertEquals(F2, tuple3.channelData.getLastPublishedIndex("key"));
	}

	@Test
	public void followeeData() throws Throwable
	{
		FolloweeData followeeData = FolloweeData.createEmpty();
		followeeData.createNewFollowee(MockKeys.K1, F1, 0L);
		followeeData.addElement(MockKeys.K1, new FollowingCacheElement(F2, F3, null, 5L));
		byte[] v2 = _serializeV2(null, null, followeeData);
		
		Tuple tuple2 = _deserializeV2(v2);
		Assert.assertEquals(1, tuple2.followeeData.getAllKnownFollowees().size());
		Assert.assertEquals(MockKeys.K1, tuple2.followeeData.getAllKnownFollowees().iterator().next());
		Assert.assertEquals(1, tuple2.followeeData.snapshotAllElementsForFollowee(MockKeys.K1).size());
		Assert.assertEquals(F3, tuple2.followeeData.snapshotAllElementsForFollowee(MockKeys.K1).get(F2).imageHash());
		byte[] v3 = _serializeV3(tuple2.channelData, tuple2.prefsData, tuple2.followeeData);
		
		Tuple tuple3 = _deserializeV3(v3);
		Assert.assertEquals(1, tuple3.followeeData.getAllKnownFollowees().size());
		Assert.assertEquals(MockKeys.K1, tuple3.followeeData.getAllKnownFollowees().iterator().next());
		Assert.assertEquals(1, tuple3.followeeData.snapshotAllElementsForFollowee(MockKeys.K1).size());
		Assert.assertEquals(F3, tuple3.followeeData.snapshotAllElementsForFollowee(MockKeys.K1).get(F2).imageHash());
	}

	@Test
	public void prefsData() throws Throwable
	{
		PrefsData prefsData = PrefsData.defaultPrefs();
		prefsData.videoEdgePixelMax = 5;
		prefsData.followCacheTargetBytes = 10L;
		byte[] v2 = _serializeV2(null, prefsData, null);
		
		Tuple tuple2 = _deserializeV2(v2);
		Assert.assertEquals(5, tuple2.prefsData.videoEdgePixelMax);
		Assert.assertEquals(10L, tuple2.prefsData.followCacheTargetBytes);
		Assert.assertEquals(PrefsData.DEFAULT_FOLLOWEE_REFRESH_MILLIS, tuple2.prefsData.followeeRefreshMillis);
		Assert.assertEquals(PrefsData.DEFAULT_REPUBLISH_INTERVAL_MILLIS, tuple2.prefsData.republishIntervalMillis);
		byte[] v3 = _serializeV3(tuple2.channelData, tuple2.prefsData, tuple2.followeeData);
		
		Tuple tuple3 = _deserializeV3(v3);
		Assert.assertEquals(5, tuple3.prefsData.videoEdgePixelMax);
		Assert.assertEquals(10L, tuple3.prefsData.followCacheTargetBytes);
		Assert.assertEquals(PrefsData.DEFAULT_FOLLOWEE_REFRESH_MILLIS, tuple3.prefsData.followeeRefreshMillis);
		Assert.assertEquals(PrefsData.DEFAULT_REPUBLISH_INTERVAL_MILLIS, tuple3.prefsData.republishIntervalMillis);
	}


	private byte[] _serializeV2(ChannelData channelData, PrefsData prefsData, FolloweeData followeeData) throws IOException
	{
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		try (ObjectOutputStream stream = com.jeffdisher.cacophony.data.local.v2.OpcodeContext.createOutputStream(outStream))
		{
			if (null != channelData)
			{
				channelData.serializeToOpcodeStream(stream);
			}
			if (null != prefsData)
			{
				prefsData.serializeToOpcodeStream(stream);
			}
			if (null != followeeData)
			{
				followeeData.serializeToOpcodeStream(stream);
			}
		}
		return outStream.toByteArray();
	}

	private Tuple _deserializeV2(byte[] data) throws IOException
	{
		ByteArrayInputStream inStream = new ByteArrayInputStream(data);
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		node.addNewKey("key", MockKeys.K1);
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Projections projections;
		try
		{
			projections = ProjectionBuilder.buildProjectionsFromOpcodeStream(scheduler, inStream);
		}
		finally
		{
			scheduler.shutdown();
		}
		return new Tuple(projections.channel(), projections.prefs(), projections.followee());
	}

	private byte[] _serializeV3(ChannelData channelData, PrefsData prefsData, FolloweeData followeeData) throws IOException
	{
		ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
		try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(outBytes))
		{
			if (null != channelData)
			{
				channelData.serializeToOpcodeWriter(writer);
			}
			if (null != prefsData)
			{
				prefsData.serializeToOpcodeWriter(writer);
			}
			if (null != followeeData)
			{
				followeeData.serializeToOpcodeWriter(writer);
			}
		}
		return outBytes.toByteArray();
	}

	private Tuple _deserializeV3(byte[] byteArray) throws IOException
	{
		ChannelData channelData = ChannelData.create();
		PrefsData prefs = PrefsData.defaultPrefs();
		FolloweeData followees = FolloweeData.createEmpty();
		FavouritesCacheData favouritesCache = new FavouritesCacheData();
		ExplicitCacheData explicitCache = new ExplicitCacheData();
		OpcodeContext context = new OpcodeContext(channelData, prefs, followees, favouritesCache, explicitCache);
		OpcodeCodec.decodeWholeStream(new ByteArrayInputStream(byteArray), context);
		return new Tuple(channelData, prefs, followees);
	}


	private static record Tuple(ChannelData channelData, PrefsData prefsData, FolloweeData followeeData) {}
}
