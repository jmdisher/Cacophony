package com.jeffdisher.cacophony.types;

import io.ipfs.multihash.Multihash;


/**
 * Since the Multihash object is used both for CID references and for public keys, this is a wrapper specifically for CIDs.
 */
public record IpfsFile(Multihash cid)
{
	public static IpfsFile fromIpfsCid(String base58Cid)
	{
		return new IpfsFile(Multihash.fromBase58(base58Cid));
	}
}
