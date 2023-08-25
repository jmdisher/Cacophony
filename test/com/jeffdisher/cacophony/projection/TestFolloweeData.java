package com.jeffdisher.cacophony.projection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v3.OpcodeContextV3;
import com.jeffdisher.cacophony.data.local.v4.OpcodeCodec;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestFolloweeData
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] {3});

	@Test
	public void serializeEmpty() throws Throwable
	{
		FolloweeData data = FolloweeData.createEmpty();
		byte[] between = _serializeAsOpcodeStream(data);
		FolloweeData read = _decodeOpcodeStream(between);
		Assert.assertNotNull(read);
		byte[] check = _serializeAsOpcodeStream(data);
		Assert.assertArrayEquals(between, check);
	}

	@Test
	public void addSingleFollowee() throws Throwable
	{
		FolloweeData data = FolloweeData.createEmpty();
		data.createNewFollowee(MockKeys.K1, F1, 0L);
		data.addElement(MockKeys.K1, new FollowingCacheElement(F1, F2, null, 5));
		data.updateExistingFollowee(MockKeys.K1, F2, 2L);
		
		Map<IpfsFile, FollowingCacheElement> cachedEntries = data.snapshotAllElementsForFollowee(MockKeys.K1);
		Assert.assertEquals(1, data.getAllKnownFollowees().size());
		Assert.assertEquals(MockKeys.K1, data.getAllKnownFollowees().iterator().next());
		Assert.assertEquals(5, cachedEntries.get(F1).combinedSizeBytes());
		Assert.assertEquals(1, cachedEntries.size());
		Assert.assertEquals(F1, cachedEntries.keySet().iterator().next());
		Assert.assertEquals(F2, data.getLastFetchedRootForFollowee(MockKeys.K1));
		Assert.assertEquals(2L, data.getLastPollMillisForFollowee(MockKeys.K1));
		Assert.assertEquals(MockKeys.K1, data.getNextFolloweeToPoll());
		
		byte[] out = _serializeAsOpcodeStream(data);
		FolloweeData read = _decodeOpcodeStream(out);
		Map<IpfsFile, FollowingCacheElement> cachedEntries1 = read.snapshotAllElementsForFollowee(MockKeys.K1);
		Assert.assertEquals(5, cachedEntries1.get(F1).combinedSizeBytes());
		
		// Verify that the opcode stream works.
		byte[] byteArray = _serializeAsOpcodeStream(data);
		FolloweeData latest = _decodeOpcodeStream(byteArray);
		Map<IpfsFile, FollowingCacheElement> cachedEntries2 = latest.snapshotAllElementsForFollowee(MockKeys.K1);
		byte[] byteArray2 = _serializeAsOpcodeStream(latest);
		Assert.assertArrayEquals(byteArray, byteArray2);
		Assert.assertEquals(5, cachedEntries2.get(F1).combinedSizeBytes());
	}

	@Test
	public void addRemoveSingleFollowee() throws Throwable
	{
		FolloweeData data = FolloweeData.createEmpty();
		data.createNewFollowee(MockKeys.K1, F1, 0L);
		data.addElement(MockKeys.K1, new FollowingCacheElement(F1, F2, null, 5));
		data.updateExistingFollowee(MockKeys.K1, F2, 2L);
		
		Map<IpfsFile, FollowingCacheElement> cachedEntries = data.snapshotAllElementsForFollowee(MockKeys.K1);
		Assert.assertEquals(1, data.getAllKnownFollowees().size());
		Assert.assertEquals(MockKeys.K1, data.getAllKnownFollowees().iterator().next());
		Assert.assertEquals(5, cachedEntries.get(F1).combinedSizeBytes());
		Assert.assertEquals(1, cachedEntries.size());
		Assert.assertEquals(F1, cachedEntries.keySet().iterator().next());
		Assert.assertEquals(F2, data.getLastFetchedRootForFollowee(MockKeys.K1));
		Assert.assertEquals(2L, data.getLastPollMillisForFollowee(MockKeys.K1));
		Assert.assertEquals(MockKeys.K1, data.getNextFolloweeToPoll());
		
		data.removeElement(MockKeys.K1, F1);
		data.removeFollowee(MockKeys.K1);
		Assert.assertTrue(data.getAllKnownFollowees().isEmpty());
		
		byte[] out = _serializeAsOpcodeStream(data);
		FolloweeData read = _decodeOpcodeStream(out);
		Assert.assertTrue(read.getAllKnownFollowees().isEmpty());
		
		// Verify that the opcode stream works.
		byte[] byteArray = _serializeAsOpcodeStream(data);
		FolloweeData latest = _decodeOpcodeStream(byteArray);
		byte[] byteArray2 = _serializeAsOpcodeStream(latest);
		Assert.assertArrayEquals(byteArray, byteArray2);
		Assert.assertTrue(latest.getAllKnownFollowees().isEmpty());
	}

	@Test
	public void addTwoFollowees() throws Throwable
	{
		FolloweeData data = FolloweeData.createEmpty();
		data.createNewFollowee(MockKeys.K1, F1, 0L);
		data.addElement(MockKeys.K1, new FollowingCacheElement(F1, F2, null, 5));
		data.updateExistingFollowee(MockKeys.K1, F2, 2L);
		data.createNewFollowee(MockKeys.K2, F1, 0L);
		data.updateExistingFollowee(MockKeys.K2, F1, 3L);
		
		Map<IpfsFile, FollowingCacheElement> cachedEntries1 = data.snapshotAllElementsForFollowee(MockKeys.K1);
		Assert.assertEquals(2, data.getAllKnownFollowees().size());
		Assert.assertEquals(5, cachedEntries1.get(F1).combinedSizeBytes());
		Assert.assertEquals(1, cachedEntries1.size());
		Assert.assertEquals(F1, cachedEntries1.keySet().iterator().next());
		Assert.assertEquals(0, data.snapshotAllElementsForFollowee(MockKeys.K2).size());
		Assert.assertEquals(F2, data.getLastFetchedRootForFollowee(MockKeys.K1));
		Assert.assertEquals(2L, data.getLastPollMillisForFollowee(MockKeys.K1));
		Assert.assertEquals(MockKeys.K1, data.getNextFolloweeToPoll());
		
		byte[] out = _serializeAsOpcodeStream(data);
		FolloweeData read = _decodeOpcodeStream(out);
		Assert.assertEquals(MockKeys.K1, read.getNextFolloweeToPoll());
		
		// Verify that the opcode stream works.
		byte[] byteArray = _serializeAsOpcodeStream(data);
		FolloweeData latest = _decodeOpcodeStream(byteArray);
		byte[] byteArray2 = _serializeAsOpcodeStream(latest);
		Assert.assertArrayEquals(byteArray, byteArray2);
		Assert.assertEquals(MockKeys.K1, latest.getNextFolloweeToPoll());
	}

	@Test
	public void addRemoveElements() throws Throwable
	{
		FolloweeData data = FolloweeData.createEmpty();
		data.createNewFollowee(MockKeys.K1, F1, 0L);
		data.addElement(MockKeys.K1, new FollowingCacheElement(F1, F2, null, 5));
		data.addElement(MockKeys.K1, new FollowingCacheElement(F2, F3, null, 6));
		data.addElement(MockKeys.K1, new FollowingCacheElement(F3, null, null, 0));
		data.updateExistingFollowee(MockKeys.K1, F2, 2L);
		
		Map<IpfsFile, FollowingCacheElement> cachedEntries = data.snapshotAllElementsForFollowee(MockKeys.K1);
		Assert.assertEquals(1, data.getAllKnownFollowees().size());
		Assert.assertEquals(MockKeys.K1, data.getAllKnownFollowees().iterator().next());
		Assert.assertEquals(5, cachedEntries.get(F1).combinedSizeBytes());
		Assert.assertEquals(6, cachedEntries.get(F2).combinedSizeBytes());
		Assert.assertEquals(0, cachedEntries.get(F3).combinedSizeBytes());
		Assert.assertEquals(3, cachedEntries.size());
		List<IpfsFile> cids = List.copyOf(cachedEntries.keySet());
		// Note that the cache elements aren't exposed to use in a deterministic order.
		Assert.assertTrue(cids.contains(F1));
		Assert.assertTrue(cids.contains(F2));
		Assert.assertTrue(cids.contains(F3));
		Assert.assertEquals(F2, data.getLastFetchedRootForFollowee(MockKeys.K1));
		Assert.assertEquals(2L, data.getLastPollMillisForFollowee(MockKeys.K1));
		Assert.assertEquals(MockKeys.K1, data.getNextFolloweeToPoll());
		
		byte[] out = _serializeAsOpcodeStream(data);
		FolloweeData read = _decodeOpcodeStream(out);
		Map<IpfsFile, FollowingCacheElement> cachedEntries1 = read.snapshotAllElementsForFollowee(MockKeys.K1);
		Assert.assertEquals(5, cachedEntries1.get(F1).combinedSizeBytes());
		
		read.removeElement(MockKeys.K1, F1);
		read.removeElement(MockKeys.K1, F2);
		// (we need to re-read the map since this will be changed)
		cachedEntries1 = read.snapshotAllElementsForFollowee(MockKeys.K1);
		Assert.assertEquals(1, cachedEntries1.size());
		Assert.assertEquals(F3, cachedEntries1.keySet().iterator().next());
		
		// Verify that the opcode stream works.
		byte[] byteArray = _serializeAsOpcodeStream(read);
		FolloweeData latest = _decodeOpcodeStream(byteArray);
		Map<IpfsFile, FollowingCacheElement> cachedEntries2 = latest.snapshotAllElementsForFollowee(MockKeys.K1);
		byte[] byteArray2 = _serializeAsOpcodeStream(latest);
		Assert.assertArrayEquals(byteArray, byteArray2);
		Assert.assertEquals(1, cachedEntries2.size());
		Assert.assertEquals(F3, cachedEntries2.keySet().iterator().next());
	}

	@Test
	public void listenForRefresh() throws Throwable
	{
		// The dispatcher is expected to lock-step execution, so we synchronize the call as a simple approach.
		Consumer<Runnable> dispatcher = new Consumer<>() {
			@Override
			public void accept(Runnable arg0)
			{
				synchronized (this)
				{
					arg0.run();
				}
			}
		};
		
		// We will run all callbacks inline so we will just use a map to observe the state.
		Map<IpfsKey, Long> map = new HashMap<>();
		FolloweeData data = FolloweeData.createEmpty();
		
		// Start with an existing followee to make sure it is observed in the map.
		data.createNewFollowee(MockKeys.K1, F1, 0L);
		data.updateExistingFollowee(MockKeys.K1, F1, 1L);
		HandoffConnector<IpfsKey, Long> connector = new HandoffConnector<>(dispatcher);
		connector.registerListener(new HandoffConnector.IHandoffListener<IpfsKey, Long>()
		{
			@Override
			public boolean update(IpfsKey key, Long value)
			{
				Assert.assertNotNull(map.put(key, value));
				return true;
			}
			@Override
			public boolean destroy(IpfsKey key)
			{
				Assert.assertNotNull(map.remove(key));
				return true;
			}
			@Override
			public boolean create(IpfsKey key, Long value, boolean isNewest)
			{
				Assert.assertNull(map.put(key, value));
				return true;
			}
			@Override
			public boolean specialChanged(String special)
			{
				throw new AssertionError("Not used");
			}
		}, 0);
		data.attachRefreshConnector(connector);
		Assert.assertEquals(1, map.size());
		Assert.assertEquals(1L, map.get(MockKeys.K1).longValue());
		
		// Add a second followee and update both of them to verify we see both updated values.
		data.createNewFollowee(MockKeys.K2, F1, 0L);
		data.updateExistingFollowee(MockKeys.K1, F2, 3L);
		data.updateExistingFollowee(MockKeys.K2, F2, 4L);
		Assert.assertEquals(2, map.size());
		Assert.assertEquals(3L, map.get(MockKeys.K1).longValue());
		Assert.assertEquals(4L, map.get(MockKeys.K2).longValue());
		
		// Remove them both and make sure the map is empty.
		data.removeFollowee(MockKeys.K1);
		data.removeFollowee(MockKeys.K2);
		Assert.assertTrue(map.isEmpty());
	}


	private byte[] _serializeAsOpcodeStream(FolloweeData data) throws IOException
	{
		ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
		try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(outBytes))
		{
			data.serializeToOpcodeWriter(writer);
		}
		return outBytes.toByteArray();
	}

	private FolloweeData _decodeOpcodeStream(byte[] byteArray) throws IOException
	{
		ChannelData channelData = null;
		PrefsData prefs = null;
		FolloweeData followees = FolloweeData.createEmpty();
		FavouritesCacheData favouritesCache = new FavouritesCacheData();
		ExplicitCacheData explicitCache = new ExplicitCacheData();
		OpcodeContextV3 context = new OpcodeContextV3(channelData, prefs, followees, favouritesCache, explicitCache, null);
		OpcodeCodec.decodeWholeStreamV3(new ByteArrayInputStream(byteArray), context);
		return followees;
	}
}
