package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.CacheIndex;
import com.jeffdisher.cacophony.utils.Types;

import io.ipfs.multihash.Multihash;


public class TestCacheIndex
{
	public static final Multihash M1 = Types.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final Multihash M2 = Types.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final Multihash M3 = Types.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC");

	@Test
	public void testBasicUse()
	{
		CacheIndex index = CacheIndex.newCache();
		index.hashWasAdded(M1);
		Assert.assertTrue(index.shouldPinAfterAdding(M2));
		Assert.assertFalse(index.shouldPinAfterAdding(M1));
		Assert.assertFalse(index.shouldPinAfterAdding(M2));
		Assert.assertFalse(index.shouldUnpinAfterRemoving(M1));
		Assert.assertTrue(index.shouldUnpinAfterRemoving(M1));
	}

	@Test
	public void testEmptySerializeDeserialize()
	{
		CacheIndex index = CacheIndex.newCache();
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		index.writeToStream(outStream);
		
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		CacheIndex read = CacheIndex.fromStream(inStream);
		Assert.assertNotNull(read);
	}

	@Test
	public void testSerializeDeserialize()
	{
		CacheIndex index = CacheIndex.newCache();
		index.hashWasAdded(M1);
		Assert.assertTrue(index.shouldPinAfterAdding(M2));
		Assert.assertFalse(index.shouldPinAfterAdding(M1));
		Assert.assertFalse(index.shouldPinAfterAdding(M2));
		Assert.assertTrue(index.shouldPinAfterAdding(M3));
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		index.writeToStream(outStream);
		
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		CacheIndex read = CacheIndex.fromStream(inStream);
		Assert.assertFalse(read.shouldPinAfterAdding(M2));
		Assert.assertFalse(read.shouldUnpinAfterRemoving(M1));
		Assert.assertTrue(read.shouldUnpinAfterRemoving(M1));
		Assert.assertFalse(read.shouldUnpinAfterRemoving(M2));
		Assert.assertFalse(read.shouldUnpinAfterRemoving(M2));
		Assert.assertTrue(read.shouldUnpinAfterRemoving(M2));
		Assert.assertTrue(read.shouldUnpinAfterRemoving(M3));
		outStream = new ByteArrayOutputStream();
		index.writeToStream(outStream);
		
		inStream = new ByteArrayInputStream(outStream.toByteArray());
		read = CacheIndex.fromStream(inStream);
		Assert.assertNotNull(read);
	}
}
