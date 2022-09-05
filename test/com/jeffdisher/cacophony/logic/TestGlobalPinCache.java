package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestGlobalPinCache
{
	public static final IpfsFile M1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile M2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final IpfsFile M3 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC");

	@Test
	public void testBasicUse()
	{
		GlobalPinCache index = GlobalPinCache.newCache();
		Assert.assertFalse(index.isCached(M1));
		index.hashWasAdded(M1);
		Assert.assertTrue(index.isCached(M1));
		Assert.assertTrue(index.shouldPinAfterAdding(M2));
		Assert.assertFalse(index.shouldPinAfterAdding(M1));
		Assert.assertFalse(index.shouldPinAfterAdding(M2));
		Assert.assertFalse(index.shouldUnpinAfterRemoving(M1));
		Assert.assertTrue(index.shouldUnpinAfterRemoving(M1));
		Assert.assertFalse(index.isCached(M1));
	}

	@Test
	public void testEmptySerializeDeserialize()
	{
		GlobalPinCache index = GlobalPinCache.newCache();
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		index.writeToStream(outStream);
		
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		GlobalPinCache read = GlobalPinCache.fromStream(inStream);
		Assert.assertNotNull(read);
		Assert.assertFalse(read.isCached(M1));
	}

	@Test
	public void testSerializeDeserialize()
	{
		GlobalPinCache index = GlobalPinCache.newCache();
		index.hashWasAdded(M1);
		Assert.assertTrue(index.shouldPinAfterAdding(M2));
		Assert.assertFalse(index.shouldPinAfterAdding(M1));
		Assert.assertFalse(index.shouldPinAfterAdding(M2));
		Assert.assertTrue(index.shouldPinAfterAdding(M3));
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		index.writeToStream(outStream);
		
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		GlobalPinCache read = GlobalPinCache.fromStream(inStream);
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
		read = GlobalPinCache.fromStream(inStream);
		Assert.assertNotNull(read);
	}

	@Test
	public void testMutableClone()
	{
		GlobalPinCache index = GlobalPinCache.newCache();
		index.hashWasAdded(M1);
		Assert.assertTrue(index.isCached(M1));
		
		GlobalPinCache copy = index.mutableClone();
		Assert.assertTrue(copy.shouldUnpinAfterRemoving(M1));
		Assert.assertFalse(copy.isCached(M1));
		Assert.assertTrue(index.isCached(M1));
	}
}
