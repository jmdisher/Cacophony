package com.jeffdisher.cacophony.projection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v2.OpcodeContext;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestPinCacheData
{
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");

	@Test
	public void serializeEmpty() throws Throwable
	{
		PinCacheData pinCache = PinCacheData.createEmpty();
		byte[] between = _serialize(pinCache);
		PinCacheData read = _deserialize(between);
		Assert.assertNotNull(read);
		byte[] check = _serialize(pinCache);
		Assert.assertArrayEquals(between, check);
	}

	@Test
	public void checkBasics() throws Throwable
	{
		PinCacheData pinCache = PinCacheData.createEmpty();
		Assert.assertFalse(pinCache.isPinned(F1));
		pinCache.addRef(F1);
		Assert.assertTrue(pinCache.isPinned(F1));
		Assert.assertFalse(pinCache.isPinned(F2));
		byte[] between = _serialize(pinCache);
		
		PinCacheData read = _deserialize(between);
		Assert.assertTrue(read.isPinned(F1));
		Assert.assertFalse(read.isPinned(F2));
		read.addRef(F2);
		read.addRef(F2);
		read.delRef(F1);
		read.delRef(F2);
		Assert.assertFalse(read.isPinned(F1));
		Assert.assertTrue(read.isPinned(F2));
	}

	@Test
	public void comparison() throws Throwable
	{
		PinCacheData pinCache1 = PinCacheData.createEmpty();
		PinCacheData pinCache2 = PinCacheData.createEmpty();
		Assert.assertTrue(pinCache1.verifyMatch(pinCache2));
		pinCache1.addRef(F1);
		Assert.assertFalse(pinCache1.verifyMatch(pinCache2));
		pinCache2.addRef(F1);
		Assert.assertTrue(pinCache1.verifyMatch(pinCache2));
		pinCache1.addRef(F1);
		Assert.assertFalse(pinCache1.verifyMatch(pinCache2));
	}


	private byte[] _serialize(PinCacheData data) throws IOException
	{
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		try (ObjectOutputStream stream = OpcodeContext.createOutputStream(outStream))
		{
			data.serializeToOpcodeStream(stream);
		}
		return outStream.toByteArray();
	}

	private PinCacheData _deserialize(byte[] data) throws IOException
	{
		ByteArrayInputStream inStream = new ByteArrayInputStream(data);
		return ProjectionBuilder.buildProjectionsFromOpcodeStream(inStream).pinCache();
	}
}
