package com.jeffdisher.cacophony.projection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestPinCacheData
{
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");

	@Test
	public void serializeEmpty()
	{
		PinCacheData pinCache = PinCacheData.buildOnCache(GlobalPinCache.newCache());
		byte[] between = _serialize(pinCache);
		PinCacheData read = _deserialize(between);
		Assert.assertNotNull(read);
		byte[] check = _serialize(pinCache);
		Assert.assertArrayEquals(between, check);
	}

	@Test
	public void checkBasics()
	{
		PinCacheData pinCache = PinCacheData.buildOnCache(GlobalPinCache.newCache());
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


	private byte[] _serialize(PinCacheData data)
	{
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		data.serializeToPinCache().writeToStream(outStream);
		return outStream.toByteArray();
	}

	private PinCacheData _deserialize(byte[] data)
	{
		ByteArrayInputStream inStream = new ByteArrayInputStream(data);
		return PinCacheData.buildOnCache(GlobalPinCache.fromStream(inStream));
	}
}
