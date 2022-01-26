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

	/**
	 * Tests the conversion between different encodings of the key.  For more details, check the decoding here:
	 * https://github.com/multiformats/java-multibase/blob/master/src/main/java/io/ipfs/multibase/Multibase.java
	 */
	@Test
	public void testKeyEncodings()
	{
		String base36 = "k51qzi5uqu5diuxe7gg1wgrla4c5l1bbg4mw2f574t71wpx1dkkk6eo54pi3ke";
		IpfsKey key = IpfsKey.fromPublicKey(base36);
		Assert.assertEquals("z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV", key.key().toString());
	}
}
