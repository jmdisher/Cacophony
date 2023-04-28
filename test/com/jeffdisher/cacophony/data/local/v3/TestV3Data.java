package com.jeffdisher.cacophony.data.local.v3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestV3Data
{
	private static final IpfsKey KEY1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final IpfsKey KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");

	@Test
	public void testCommonData() throws Throwable
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(out))
		{
			writer.writeOpcode(new Opcode_DefineChannel("key name", KEY1, MockSingleNode.generateHash(new byte[] {1, 2, 3})));
			writer.writeOpcode(new Opcode_SetPrefsInt(PrefsData.INT_VIDEO_EDGE, 1));
			writer.writeOpcode(new Opcode_SetPrefsLong(PrefsData.LONG_FOLLOW_CACHE_BYTES, 2L));
		}
		
		ChannelData channels = ChannelData.create();
		PrefsData prefs = PrefsData.defaultPrefs();
		FolloweeData followees = null;
		OpcodeContext context = new OpcodeContext(channels, prefs, followees);
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
			writer.writeOpcode(new Opcode_SetFolloweeState(KEY1, root1, 0L));
			writer.writeOpcode(new Opcode_SetFolloweeState(KEY2, root2, 1L));
			writer.writeOpcode(new Opcode_AddFolloweeElement(KEY2, elt1, image1, null, 5L));
		}
		
		ChannelData channels = ChannelData.create();
		PrefsData prefs = null;
		FolloweeData followees = FolloweeData.createEmpty();
		OpcodeContext context = new OpcodeContext(channels, prefs, followees);
		try (ByteArrayInputStream input = new ByteArrayInputStream(out.toByteArray()))
		{
			OpcodeCodec.decodeWholeStream(input, context);
		}
		
		Assert.assertEquals(2, followees.getAllKnownFollowees().size());
		Assert.assertEquals(root1, followees.getLastFetchedRootForFollowee(KEY1));
		Assert.assertEquals(root2, followees.getLastFetchedRootForFollowee(KEY2));
		Map<IpfsFile, FollowingCacheElement> elts = followees.snapshotAllElementsForFollowee(KEY2);
		Assert.assertEquals(1, elts.size());
		Assert.assertEquals(image1, elts.get(elt1).imageHash());
	}
}
