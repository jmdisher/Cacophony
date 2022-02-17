package com.jeffdisher.cacophony.types;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import com.jeffdisher.cacophony.utils.Assert;

import io.ipfs.cid.Cid;
import io.ipfs.multihash.Multihash;


/**
 * Since the Multihash object is used both for CID references and for public keys, this is a wrapper specifically for keys.
 * NOTE:  Keys have multiple encodings and details of the different encodings can be found here:
 * https://github.com/multiformats/java-multibase/blob/master/src/main/java/io/ipfs/multibase/Multibase.java
 * 
 * For context, the keys output by "ipfs key gen" are base36 (start with k) but the data coming back from key lookups
 * against the node are base58 (start with z).
 * An example of this is k51qzi5uqu5diuxe7gg1wgrla4c5l1bbg4mw2f574t71wpx1dkkk6eo54pi3ke which can also be interpreted as
 * z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV.
 * 
 * While we canonically use base58 representations, this helper can read any of these.
 */
public class IpfsKey implements Serializable
{
	private static final long serialVersionUID = 1L;
	public static IpfsKey fromPublicKey(String keyAsString)
	{
		Assert.assertTrue(null != keyAsString);
		return new IpfsKey(Cid.decode(keyAsString));
	}


	private Multihash _key;

	public IpfsKey(Multihash key)
	{
		Assert.assertTrue(null != key);
		_key = key;
	}

	public Multihash getMultihash()
	{
		return _key;
	}

	public String toPublicKey()
	{
		// Note that toBase58 doesn't give the "z" prefix so we use the toString() helper.
		return _key.toString();
	}

	@Override
	public boolean equals(Object obj)
	{
		boolean isEqual = false;
		if (obj instanceof IpfsKey)
		{
			isEqual = _key.equals(((IpfsKey)obj)._key);
		}
		return isEqual;
	}

	@Override
	public int hashCode()
	{
		return _key.hashCode();
	}

	@Override
	public String toString()
	{
		return "IpfsKey(" + _key.toString() + ")";
	}

	private void readObject(ObjectInputStream inputStream) throws ClassNotFoundException, IOException {
		_key = Cid.decode(inputStream.readUTF());
	}

	private void writeObject(ObjectOutputStream outputStream) throws IOException {
		outputStream.writeUTF(_key.toString());
	}
}
