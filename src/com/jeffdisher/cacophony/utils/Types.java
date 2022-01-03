package com.jeffdisher.cacophony.utils;

import io.ipfs.cid.Cid;
import io.ipfs.multihash.Multihash;


public class Types
{
	public static Multihash fromIpfsCid(String base58Cid)
	{
		return Multihash.fromBase58(base58Cid);
	}

	public static Multihash fromPublicKey(String base64Key)
	{
		return Cid.decode(base64Key);
	}
}
