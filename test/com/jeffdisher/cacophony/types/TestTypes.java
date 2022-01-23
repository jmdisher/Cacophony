package com.jeffdisher.cacophony.types;

import org.junit.Assert;
import org.junit.Test;


public class TestTypes
{
	@Test
	public void testFile()
	{
		String input = "QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG";
		IpfsFile file = IpfsFile.fromIpfsCid(input);
		Assert.assertNotNull(file);
		Assert.assertEquals(input, file.cid().toString());
		Assert.assertEquals(file.cid().toBase58(), file.cid().toString());
	}

	@Test
	public void testKey()
	{
		String input = "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F";
		IpfsKey key = IpfsKey.fromPublicKey(input);
		Assert.assertNotNull(key);
		Assert.assertEquals(input, key.key().toString());
		Assert.assertEquals("z" + key.key().toBase58(), key.key().toString());
	}
}
