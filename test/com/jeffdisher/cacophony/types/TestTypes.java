package com.jeffdisher.cacophony.types;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;


public class TestTypes
{
	@Test
	public void testFile()
	{
		String input = "QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG";
		IpfsFile file = IpfsFile.fromIpfsCid(input);
		Assert.assertNotNull(file);
		Assert.assertEquals(input, file.toSafeString());
		Assert.assertEquals(file.getMultihash().toBase58(), file.getMultihash().toString());
		Assert.assertEquals(file.getMultihash().toString(), file.toSafeString());
	}

	@Test
	public void testKey()
	{
		String input = "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F";
		IpfsKey key = IpfsKey.fromPublicKey(input);
		Assert.assertNotNull(key);
		Assert.assertEquals(input, key.toPublicKey());
		Assert.assertEquals("z" + key.getMultihash().toBase58(), key.getMultihash().toString());
		Assert.assertEquals(key.getMultihash().toString(), key.toPublicKey());
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
		Assert.assertEquals("z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV", key.toPublicKey());
	}

	@Test
	public void testIpfsFileSerialization() throws IOException, ClassNotFoundException
	{
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		ObjectOutputStream objectOutputStream = new ObjectOutputStream(outStream);
		String input = "QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG";
		IpfsFile file = IpfsFile.fromIpfsCid(input);
		objectOutputStream.writeObject(file);
		objectOutputStream.close();
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		ObjectInputStream objectInputStream = new ObjectInputStream(inStream);
		IpfsFile newFile = (IpfsFile) objectInputStream.readObject();
		objectInputStream.close();
		Assert.assertEquals(file, newFile);
	}

	@Test
	public void testIpfsKeySerialization() throws IOException, ClassNotFoundException
	{
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		ObjectOutputStream objectOutputStream = new ObjectOutputStream(outStream);
		String input = "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F";
		IpfsKey key = IpfsKey.fromPublicKey(input);
		objectOutputStream.writeObject(key);
		objectOutputStream.close();
		ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
		ObjectInputStream objectInputStream = new ObjectInputStream(inStream);
		IpfsKey newKey = (IpfsKey) objectInputStream.readObject();
		objectInputStream.close();
		Assert.assertEquals(key, newKey);
	}

	@Test
	public void testJsonObject()
	{
		JsonObject object = new JsonObject();
		object.set("bool", false);
		object.set("double", 5.6d);
		object.set("string", "string value\"\'");
		JsonObject nested = new JsonObject();
		nested.add("long", 1_000_000_000L);
		object.set("object", nested);
		JsonArray array = new JsonArray();
		array.add("value");
		object.set("array", array);
		Assert.assertEquals("{\"bool\":false,\"double\":5.6,\"string\":\"string value\\\"'\",\"object\":{\"long\":1000000000},\"array\":[\"value\"]}", object.toString());
	}

	@Test
	public void testJsonArray()
	{
		JsonArray array = new JsonArray();
		JsonObject nested = new JsonObject();
		nested.add("long", 1_000_000_000L);
		array.add(nested);
		JsonArray other = new JsonArray();
		array.add(other);
		Assert.assertEquals("[{\"long\":1000000000},[]]", array.toString());
	}
}
