package com.jeffdisher.cacophony.types;

import io.ipfs.cid.Cid;
import io.ipfs.multihash.Multihash;


/**
 * Since the Multihash object is used both for CID references and for public keys, this is a wrapper specifically for keys.
 * Keys are represented by strings similar to "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F".
 */
public record IpfsKey(Multihash key)
{
	public static IpfsKey fromPublicKey(String keyAsString)
	{
		return new IpfsKey(Cid.decode(keyAsString));
	}
}
