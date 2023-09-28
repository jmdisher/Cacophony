package com.jeffdisher.cacophony.types;

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
public class IpfsKey
{
	/**
	 * @param keyAsString The base-36 or base-58 encoding of a public key.
	 * @return The IpfsKey or null if the encoding was invalid.
	 */
	public static IpfsKey fromPublicKey(String keyAsString)
	{
		IpfsKey key = null;
		try
		{
			key = new IpfsKey(Cid.decode(keyAsString));
		}
		catch (IllegalStateException e)
		{
			// This happens if the prefix is confusing ("Unknown Multibase type").
			key = null;
		}
		catch (Cid.CidEncodingException e)
		{
			key = null;
		}
		return key;
	}


	private Multihash _key;

	public IpfsKey(Multihash key)
	{
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
}
