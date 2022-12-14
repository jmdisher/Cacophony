package com.jeffdisher.cacophony.projection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

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
		
		Assert.assertEquals(1, data.getAllKnownFollowees().size());
		Assert.assertEquals(K1, data.getAllKnownFollowees().iterator().next());
		Assert.assertEquals(5, data.getElementForFollowee(K1, F1).combinedSizeBytes());
		Assert.assertEquals(1, data.getElementsForFollowee(K1).size());
		Assert.assertEquals(F1, data.getElementsForFollowee(K1).get(0));
		Assert.assertEquals(F2, data.getLastFetchedRootForFollowee(K1));
		Assert.assertEquals(2L, data.getLastPollMillisForFollowee(K1));
		Assert.assertEquals(K1, data.getNextFolloweeToPoll());
		
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		data.serializeToIndex().writeToStream(outStream);
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		FolloweeData read = FolloweeData.buildOnIndex(FollowIndex.fromStream(inStream));
		Assert.assertEquals(5, read.getElementForFollowee(K1, F1).combinedSizeBytes());
		
		// Verify that the opcode stream works.
		byte[] byteArray = _serializeAsOpcodeStream(data);
		FolloweeData latest = _decodeOpcodeStream(byteArray);
		byte[] byteArray2 = _serializeAsOpcodeStream(latest);
		Assert.assertArrayEquals(byteArray, byteArray2);
		Assert.assertEquals(5, latest.getElementForFollowee(K1, F1).combinedSizeBytes());
	}

	@Test
	public void addRemoveSingleFollowee() throws Throwable
	{
		FolloweeData data = FolloweeData.buildOnIndex(FollowIndex.emptyFollowIndex());
		data.createNewFollowee(K1, F1, 1L);
		data.addElement(K1, new FollowingCacheElement(F1, F2, null, 5));
		data.updateExistingFollowee(K1, F2, 2L);
		
		Assert.assertEquals(1, data.getAllKnownFollowees().size());
		Assert.assertEquals(K1, data.getAllKnownFollowees().iterator().next());
		Assert.assertEquals(5, data.getElementForFollowee(K1, F1).combinedSizeBytes());
		Assert.assertEquals(1, data.getElementsForFollowee(K1).size());
		Assert.assertEquals(F1, data.getElementsForFollowee(K1).get(0));
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
		
		Assert.assertEquals(2, data.getAllKnownFollowees().size());
		Assert.assertEquals(5, data.getElementForFollowee(K1, F1).combinedSizeBytes());
		Assert.assertEquals(1, data.getElementsForFollowee(K1).size());
		Assert.assertEquals(F1, data.getElementsForFollowee(K1).get(0));
		Assert.assertEquals(0, data.getElementsForFollowee(K2).size());
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
		
		Assert.assertEquals(1, data.getAllKnownFollowees().size());
		Assert.assertEquals(K1, data.getAllKnownFollowees().iterator().next());
		Assert.assertEquals(5, data.getElementForFollowee(K1, F1).combinedSizeBytes());
		Assert.assertEquals(6, data.getElementForFollowee(K1, F2).combinedSizeBytes());
		Assert.assertEquals(0, data.getElementForFollowee(K1, F3).combinedSizeBytes());
		Assert.assertEquals(3, data.getElementsForFollowee(K1).size());
		Assert.assertEquals(F1, data.getElementsForFollowee(K1).get(0));
		Assert.assertEquals(F2, data.getElementsForFollowee(K1).get(1));
		Assert.assertEquals(F3, data.getElementsForFollowee(K1).get(2));
		Assert.assertEquals(F2, data.getLastFetchedRootForFollowee(K1));
		Assert.assertEquals(2L, data.getLastPollMillisForFollowee(K1));
		Assert.assertEquals(K1, data.getNextFolloweeToPoll());
		
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		data.serializeToIndex().writeToStream(outStream);
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		FolloweeData read = FolloweeData.buildOnIndex(FollowIndex.fromStream(inStream));
		Assert.assertEquals(5, read.getElementForFollowee(K1, F1).combinedSizeBytes());
		
		read.removeElement(K1, F1);
		read.removeElement(K1, F2);
		Assert.assertEquals(1, read.getElementsForFollowee(K1).size());
		Assert.assertEquals(F3, read.getElementsForFollowee(K1).get(0));
		
		// Verify that the opcode stream works.
		byte[] byteArray = _serializeAsOpcodeStream(read);
		FolloweeData latest = _decodeOpcodeStream(byteArray);
		byte[] byteArray2 = _serializeAsOpcodeStream(latest);
		Assert.assertArrayEquals(byteArray, byteArray2);
		Assert.assertEquals(1, latest.getElementsForFollowee(K1).size());
		Assert.assertEquals(F3, latest.getElementsForFollowee(K1).get(0));
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
