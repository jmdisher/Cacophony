package com.jeffdisher.cacophony.projection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v2.IFolloweeDecoding;
import com.jeffdisher.cacophony.data.local.v2.OpcodeContext;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestFollowData
{
	public static final IpfsKey K1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	public static final IpfsKey K2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14W");
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final IpfsFile F3 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC");

	@Test
	public void serializeEmpty() throws Throwable
	{
		FolloweeData data = FolloweeData.buildOnIndex(FollowIndex.emptyFollowIndex());
		byte[] between = _serialize(data);
		FolloweeData read = _deserialize(between);
		Assert.assertNotNull(read);
		byte[] check = _serialize(data);
		Assert.assertArrayEquals(between, check);
		
		// Verify that the opcode stream works.
		byte[] byteArray = _serializeAsOpcodeStream(data);
		FolloweeData latest = _decodeOpcodeStream(byteArray);
		byte[] byteArray2 = _serializeAsOpcodeStream(latest);
		Assert.assertArrayEquals(byteArray, byteArray2);
	}

	@Test
	public void addSingleFollowee() throws Throwable
	{
		FolloweeData data = FolloweeData.buildOnIndex(FollowIndex.emptyFollowIndex());
		data.createNewFollowee(K1, F1, 1L);
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
		
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		data.serializeToIndex().writeToStream(outStream);
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		FolloweeData read = FolloweeData.buildOnIndex(FollowIndex.fromStream(inStream));
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
		FolloweeData data = FolloweeData.buildOnIndex(FollowIndex.emptyFollowIndex());
		data.createNewFollowee(K1, F1, 1L);
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
		
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		data.serializeToIndex().writeToStream(outStream);
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		FolloweeData read = FolloweeData.buildOnIndex(FollowIndex.fromStream(inStream));
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
		FolloweeData data = FolloweeData.buildOnIndex(FollowIndex.emptyFollowIndex());
		data.createNewFollowee(K1, F1, 1L);
		data.addElement(K1, new FollowingCacheElement(F1, F2, null, 5));
		data.updateExistingFollowee(K1, F2, 2L);
		data.createNewFollowee(K2, F1, 3L);
		
		Map<IpfsFile, FollowingCacheElement> cachedEntries1 = data.snapshotAllElementsForFollowee(K1);
		Assert.assertEquals(2, data.getAllKnownFollowees().size());
		Assert.assertEquals(5, cachedEntries1.get(F1).combinedSizeBytes());
		Assert.assertEquals(1, cachedEntries1.size());
		Assert.assertEquals(F1, cachedEntries1.keySet().iterator().next());
		Assert.assertEquals(0, data.snapshotAllElementsForFollowee(K2).size());
		Assert.assertEquals(F2, data.getLastFetchedRootForFollowee(K1));
		Assert.assertEquals(2L, data.getLastPollMillisForFollowee(K1));
		Assert.assertEquals(K1, data.getNextFolloweeToPoll());
		
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		data.serializeToIndex().writeToStream(outStream);
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		FolloweeData read = FolloweeData.buildOnIndex(FollowIndex.fromStream(inStream));
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
		FolloweeData data = FolloweeData.buildOnIndex(FollowIndex.emptyFollowIndex());
		data.createNewFollowee(K1, F1, 1L);
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
		
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		data.serializeToIndex().writeToStream(outStream);
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		FolloweeData read = FolloweeData.buildOnIndex(FollowIndex.fromStream(inStream));
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


	private byte[] _serialize(FolloweeData data)
	{
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		data.serializeToIndex().writeToStream(outStream);
		return outStream.toByteArray();
	}

	private FolloweeData _deserialize(byte[] data)
	{
		ByteArrayInputStream inStream = new ByteArrayInputStream(data);
		return FolloweeData.buildOnIndex(FollowIndex.fromStream(inStream));
	}

	private byte[] _serializeAsOpcodeStream(FolloweeData data) throws IOException
	{
		ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
		try (ObjectOutputStream stream = OpcodeContext.createOutputStream(outBytes))
		{
			data.serializeToOpcodeStream(stream);
		}
		return outBytes.toByteArray();
	}

	private FolloweeData _decodeOpcodeStream(byte[] byteArray) throws IOException
	{
		FolloweeData latest = FolloweeData.createEmpty();
		// We are only decoding the followee data so we can use a null misc decoder.
		OpcodeContext context = new OpcodeContext(null, new IFolloweeDecoding()
		{
			@Override
			public void createNewFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis)
			{
				latest.createNewFollowee(followeeKey, indexRoot, lastPollMillis);
			}
			@Override
			public void addElement(IpfsKey followeeKey, IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedSizeBytes)
			{
				latest.addElement(followeeKey, new FollowingCacheElement(elementHash, imageHash, leafHash, combinedSizeBytes));
			}
		});
		ByteArrayInputStream inBytes = new ByteArrayInputStream(byteArray);
		try
		{
			context.decodeWholeStream(inBytes);
		}
		finally
		{
			inBytes.close();
		}
		return latest;
	}
}
