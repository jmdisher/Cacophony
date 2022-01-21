package com.jeffdisher.cacophony.types;

import io.ipfs.cid.Cid;
import io.ipfs.multihash.Multihash;


/**
 * Since the Multihash object is used both for CID references and for public keys, this is a wrapper specifically for keys.
 */
public record IpfsKey(Multihash key)
{
	public static IpfsKey fromPublicKey(String base64Key)
	{
		return new IpfsKey(Cid.decode(base64Key));
	}
}
