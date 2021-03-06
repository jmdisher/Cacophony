package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestFollowIndex
{
	public static final IpfsKey K1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	public static final IpfsKey K2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14W");
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final IpfsFile F3 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC");

	@Test
	public void testLoadStoreEmpty()
	{
		FollowIndex index = FollowIndex.emptyFollowIndex();
		Assert.assertEquals(null, index.nextKeyToPoll());
		Assert.assertEquals(null, index.getLastFetchedRoot(K1));
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		index.writeToStream(outStream);
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		FollowIndex read = FollowIndex.fromStream(inStream);
		Assert.assertEquals(null, read.nextKeyToPoll());
		Assert.assertEquals(null, read.getLastFetchedRoot(K1));
	}

	@Test
	public void testTwoEntryLoadStore()
	{
		FollowIndex index = FollowIndex.emptyFollowIndex();
		index.addFollowingWithInitialState(K1, F1, 1);
		index.addFollowingWithInitialState(K2, F2, 2);
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		index.writeToStream(outStream);
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		FollowIndex read = FollowIndex.fromStream(inStream);
		Assert.assertEquals(K1, read.nextKeyToPoll());
		Assert.assertEquals(K1, read.nextKeyToPoll());
		Assert.assertEquals(F1, read.getLastFetchedRoot(K1));
	}

	@Test
	public void testUpdateToElement()
	{
		FollowIndex index = FollowIndex.emptyFollowIndex();
		index.addFollowingWithInitialState(K1, F1, 1);
		index.addFollowingWithInitialState(K2, F2, 2);
		Assert.assertEquals(K1, index.nextKeyToPoll());
		index.addNewElementToFollower(K1, F3, F3, null, F3, 1000, 100);
		Assert.assertEquals(K2, index.nextKeyToPoll());
		Assert.assertEquals(F3, index.getLastFetchedRoot(K1));
	}

	@Test
	public void testRemove()
	{
		FollowIndex index = FollowIndex.emptyFollowIndex();
		index.addFollowingWithInitialState(K1, F1, 1);
		index.addFollowingWithInitialState(K2, F2, 2);
		Assert.assertEquals(K1, index.nextKeyToPoll());
		index.removeFollowing(K1);
		Assert.assertEquals(K2, index.nextKeyToPoll());
		Assert.assertEquals(K2, index.nextKeyToPoll());
		Assert.assertEquals(null, index.getLastFetchedRoot(K1));
		Assert.assertEquals(F2, index.getLastFetchedRoot(K2));
	}

	@Test
	public void testIteration()
	{
		FollowIndex index = FollowIndex.emptyFollowIndex();
		index.addFollowingWithInitialState(K1, F1, 1);
		index.addFollowingWithInitialState(K2, F2, 2);
		Iterator<FollowRecord> iter = index.iterator();
		Assert.assertEquals(K1, iter.next().publicKey());
		Assert.assertEquals(K2, iter.next().publicKey());
		Assert.assertFalse(iter.hasNext());
		
		index.removeFollowing(K2);
		iter = index.iterator();
		Assert.assertEquals(K1, iter.next().publicKey());
		Assert.assertFalse(iter.hasNext());
	}

	@Test
	public void testUpdateAndRemove()
	{
		FollowIndex index = FollowIndex.emptyFollowIndex();
		index.addFollowingWithInitialState(K1, F1, 1);
		Assert.assertNotNull(index.getFollowerRecord(K1));
		index.updateFollowee(K1, F2, 1);
		Assert.assertNotNull(index.getFollowerRecord(K1));
	}

	@Test
	public void testIndexWithEntries()
	{
		IpfsFile element1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCen1");
		IpfsFile image1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCen2");
		IpfsFile video1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCen3");
		IpfsFile element2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeC1");
		IpfsFile image2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeC2");
		IpfsFile video2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeC3");
		long currentTimeMillis = 1_000L;
		long combinedSizeBytes = 1_000_000L;
		FollowIndex index = FollowIndex.emptyFollowIndex();
		index.addFollowingWithInitialState(K1, F1, 1);
		index.addNewElementToFollower(K1, F2, element1, image1, video1, currentTimeMillis, combinedSizeBytes);
		index.addNewElementToFollower(K1, F3, element2, image2, video2, currentTimeMillis, combinedSizeBytes);
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		index.writeToStream(outStream);
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		FollowIndex read = FollowIndex.fromStream(inStream);
		// New following keys are added at the front of the list so we should see the second one.
		Assert.assertEquals(K1, read.nextKeyToPoll());
		Assert.assertEquals(F3, read.getLastFetchedRoot(K1));
		FollowRecord record = read.getFollowerRecord(K1);
		Assert.assertEquals(currentTimeMillis, record.lastPollMillis());
		FollowingCacheElement[] elements = record.elements();
		Assert.assertEquals(2, elements.length);
		Assert.assertEquals(element1, elements[0].elementHash());
		Assert.assertEquals(image1, elements[0].imageHash());
		Assert.assertEquals(video1, elements[0].leafHash());
		Assert.assertEquals(combinedSizeBytes, elements[0].combinedSizeBytes());
	}
}
