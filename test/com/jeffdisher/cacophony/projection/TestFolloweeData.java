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

import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v3.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v3.OpcodeContext;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestFolloweeData
{
	public static final IpfsKey K1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	public static final IpfsKey K2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14W");
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final IpfsFile F3 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC");

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
		data.createNewFollowee(K1, F1, 0L);
		data.addElement(K1, new FollowingCacheElement(F1, F2, null, 5));
		data.updateExistingFollowee(K1, F2, 2L);
		
		Map<IpfsFile, FollowingCacheElement> cachedEntries = data.snapshotAllElementsForFollowee(K1);
		Assert.assertEquals(1, data.getAllKnownFollowees().size());
		Assert.assertEquals(K1, data.getAllKnownFollowees().iterator().next());
		Assert.assertEquals(5, cachedEntries.get(F1).combinedSizeBytes());
		Assert.assertEquals(1, cachedEntries.size());
		Assert.assertEquals(F1, cachedEntries.keySet().iterator().next());
		Assert.assertEquals(F2, data.getLastFetchedRootForFollowee(K1));
		Assert.assertEquals(2L, data.getLastPollMillisForFollowee(K1));
		Assert.assertEquals(K1, data.getNextFolloweeToPoll());
		
		byte[] out = _serializeAsOpcodeStream(data);
		FolloweeData read = _decodeOpcodeStream(out);
		Map<IpfsFile, FollowingCacheElement> cachedEntries1 = read.snapshotAllElementsForFollowee(K1);
		Assert.assertEquals(5, cachedEntries1.get(F1).combinedSizeBytes());
		
		// Verify that the opcode stream works.
		byte[] byteArray = _serializeAsOpcodeStream(data);
		FolloweeData latest = _decodeOpcodeStream(byteArray);
		Map<IpfsFile, FollowingCacheElement> cachedEntries2 = latest.snapshotAllElementsForFollowee(K1);
		byte[] byteArray2 = _serializeAsOpcodeStream(latest);
		Assert.assertArrayEquals(byteArray, byteArray2);
		Assert.assertEquals(5, cachedEntries2.get(F1).combinedSizeBytes());
	}

	@Test
	public void addRemoveSingleFollowee() throws Throwable
	{
		FolloweeData data = FolloweeData.createEmpty();
		data.createNewFollowee(K1, F1, 0L);
		data.addElement(K1, new FollowingCacheElement(F1, F2, null, 5));
		data.updateExistingFollowee(K1, F2, 2L);
		
		Map<IpfsFile, FollowingCacheElement> cachedEntries = data.snapshotAllElementsForFollowee(K1);
		Assert.assertEquals(1, data.getAllKnownFollowees().size());
		Assert.assertEquals(K1, data.getAllKnownFollowees().iterator().next());
		Assert.assertEquals(5, cachedEntries.get(F1).combinedSizeBytes());
		Assert.assertEquals(1, cachedEntries.size());
		Assert.assertEquals(F1, cachedEntries.keySet().iterator().next());
		Assert.assertEquals(F2, data.getLastFetchedRootForFollowee(K1));
		Assert.assertEquals(2L, data.getLastPollMillisForFollowee(K1));
		Assert.assertEquals(K1, data.getNextFolloweeToPoll());
		
		data.removeElement(K1, F1);
		data.removeFollowee(K1);
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
		data.createNewFollowee(K1, F1, 0L);
		data.addElement(K1, new FollowingCacheElement(F1, F2, null, 5));
		data.updateExistingFollowee(K1, F2, 2L);
		data.createNewFollowee(K2, F1, 0L);
		data.updateExistingFollowee(K2, F1, 3L);
		
		Map<IpfsFile, FollowingCacheElement> cachedEntries1 = data.snapshotAllElementsForFollowee(K1);
		Assert.assertEquals(2, data.getAllKnownFollowees().size());
		Assert.assertEquals(5, cachedEntries1.get(F1).combinedSizeBytes());
		Assert.assertEquals(1, cachedEntries1.size());
		Assert.assertEquals(F1, cachedEntries1.keySet().iterator().next());
		Assert.assertEquals(0, data.snapshotAllElementsForFollowee(K2).size());
		Assert.assertEquals(F2, data.getLastFetchedRootForFollowee(K1));
		Assert.assertEquals(2L, data.getLastPollMillisForFollowee(K1));
		Assert.assertEquals(K1, data.getNextFolloweeToPoll());
		
		byte[] out = _serializeAsOpcodeStream(data);
		FolloweeData read = _decodeOpcodeStream(out);
		Assert.assertEquals(K1, read.getNextFolloweeToPoll());
		
		// Verify that the opcode stream works.
		byte[] byteArray = _serializeAsOpcodeStream(data);
		FolloweeData latest = _decodeOpcodeStream(byteArray);
		byte[] byteArray2 = _serializeAsOpcodeStream(latest);
		Assert.assertArrayEquals(byteArray, byteArray2);
		Assert.assertEquals(K1, latest.getNextFolloweeToPoll());
	}

	@Test
	public void addRemoveElements() throws Throwable
	{
		FolloweeData data = FolloweeData.createEmpty();
		data.createNewFollowee(K1, F1, 0L);
		data.addElement(K1, new FollowingCacheElement(F1, F2, null, 5));
		data.addElement(K1, new FollowingCacheElement(F2, F3, null, 6));
		data.addElement(K1, new FollowingCacheElement(F3, null, null, 0));
		data.updateExistingFollowee(K1, F2, 2L);
		
		Map<IpfsFile, FollowingCacheElement> cachedEntries = data.snapshotAllElementsForFollowee(K1);
		Assert.assertEquals(1, data.getAllKnownFollowees().size());
		Assert.assertEquals(K1, data.getAllKnownFollowees().iterator().next());
		Assert.assertEquals(5, cachedEntries.get(F1).combinedSizeBytes());
		Assert.assertEquals(6, cachedEntries.get(F2).combinedSizeBytes());
		Assert.assertEquals(0, cachedEntries.get(F3).combinedSizeBytes());
		Assert.assertEquals(3, cachedEntries.size());
		List<IpfsFile> cids = List.copyOf(cachedEntries.keySet());
		// Note that the cache elements aren't exposed to use in a deterministic order.
		Assert.assertTrue(cids.contains(F1));
		Assert.assertTrue(cids.contains(F2));
		Assert.assertTrue(cids.contains(F3));
		Assert.assertEquals(F2, data.getLastFetchedRootForFollowee(K1));
		Assert.assertEquals(2L, data.getLastPollMillisForFollowee(K1));
		Assert.assertEquals(K1, data.getNextFolloweeToPoll());
		
		byte[] out = _serializeAsOpcodeStream(data);
		FolloweeData read = _decodeOpcodeStream(out);
		Map<IpfsFile, FollowingCacheElement> cachedEntries1 = read.snapshotAllElementsForFollowee(K1);
		Assert.assertEquals(5, cachedEntries1.get(F1).combinedSizeBytes());
		
		read.removeElement(K1, F1);
		read.removeElement(K1, F2);
		// (we need to re-read the map since this will be changed)
		cachedEntries1 = read.snapshotAllElementsForFollowee(K1);
		Assert.assertEquals(1, cachedEntries1.size());
		Assert.assertEquals(F3, cachedEntries1.keySet().iterator().next());
		
		// Verify that the opcode stream works.
		byte[] byteArray = _serializeAsOpcodeStream(read);
		FolloweeData latest = _decodeOpcodeStream(byteArray);
		Map<IpfsFile, FollowingCacheElement> cachedEntries2 = latest.snapshotAllElementsForFollowee(K1);
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
		data.createNewFollowee(K1, F1, 0L);
		data.updateExistingFollowee(K1, F1, 1L);
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
		Assert.assertEquals(1L, map.get(K1).longValue());
		
		// Add a second followee and update both of them to verify we see both updated values.
		data.createNewFollowee(K2, F1, 0L);
		data.updateExistingFollowee(K1, F2, 3L);
		data.updateExistingFollowee(K2, F2, 4L);
		Assert.assertEquals(2, map.size());
		Assert.assertEquals(3L, map.get(K1).longValue());
		Assert.assertEquals(4L, map.get(K2).longValue());
		
		// Remove them both and make sure the map is empty.
		data.removeFollowee(K1);
		data.removeFollowee(K2);
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
		OpcodeContext context = new OpcodeContext(channelData, prefs, followees, favouritesCache, explicitCache);
		OpcodeCodec.decodeWholeStream(new ByteArrayInputStream(byteArray), context);
		return followees;
	}
}
