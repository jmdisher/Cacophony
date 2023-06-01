package com.jeffdisher.cacophony.data.local.v3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestV3Data
{
	@Test
	public void testCommonData() throws Throwable
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(out))
		{
			writer.writeOpcode(new Opcode_DefineChannel("key name", MockKeys.K1, MockSingleNode.generateHash(new byte[] {1, 2, 3})));
			writer.writeOpcode(new Opcode_SetPrefsInt(PrefsData.INT_VIDEO_EDGE, 1));
			writer.writeOpcode(new Opcode_SetPrefsLong(PrefsData.LONG_FOLLOW_CACHE_BYTES, 2L));
		}
		
		ChannelData channels = ChannelData.create();
		PrefsData prefs = PrefsData.defaultPrefs();
		FolloweeData followees = null;
		FavouritesCacheData favouritesCache = null;
		ExplicitCacheData explicitCache = null;
		OpcodeContext context = new OpcodeContext(channels, prefs, followees, favouritesCache, explicitCache);
		try (ByteArrayInputStream input = new ByteArrayInputStream(out.toByteArray()))
		{
			OpcodeCodec.decodeWholeStream(input, context);
		}
		
		Assert.assertEquals(1, channels.getKeyNames().size());
		Assert.assertEquals(1, prefs.videoEdgePixelMax);
		Assert.assertEquals(2L, prefs.followCacheTargetBytes);
	}

	@Test
	public void testFollowees() throws Throwable
	{
		IpfsFile root1 = MockSingleNode.generateHash(new byte[] {1});
		IpfsFile root2 = MockSingleNode.generateHash(new byte[] {2});
		IpfsFile elt1 = MockSingleNode.generateHash(new byte[] {3});
		IpfsFile image1 = MockSingleNode.generateHash(new byte[] {4});
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(out))
		{
			writer.writeOpcode(new Opcode_SetFolloweeState(MockKeys.K1, root1, 0L));
			writer.writeOpcode(new Opcode_SetFolloweeState(MockKeys.K2, root2, 1L));
			writer.writeOpcode(new Opcode_AddFolloweeElement(MockKeys.K2, elt1, image1, null, 5L));
		}
		
		ChannelData channels = ChannelData.create();
		PrefsData prefs = null;
		FolloweeData followees = FolloweeData.createEmpty();
		FavouritesCacheData favouritesCache = null;
		ExplicitCacheData explicitCache = new ExplicitCacheData();
		OpcodeContext context = new OpcodeContext(channels, prefs, followees, favouritesCache, explicitCache);
		try (ByteArrayInputStream input = new ByteArrayInputStream(out.toByteArray()))
		{
			OpcodeCodec.decodeWholeStream(input, context);
		}
		
		Assert.assertEquals(2, followees.getAllKnownFollowees().size());
		Assert.assertEquals(root1, followees.getLastFetchedRootForFollowee(MockKeys.K1));
		Assert.assertEquals(root2, followees.getLastFetchedRootForFollowee(MockKeys.K2));
		Map<IpfsFile, FollowingCacheElement> elts = followees.snapshotAllElementsForFollowee(MockKeys.K2);
		Assert.assertEquals(1, elts.size());
		Assert.assertEquals(image1, elts.get(elt1).imageHash());
	}
}
