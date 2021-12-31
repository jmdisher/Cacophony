package com.jeffdisher.cacophony.utils;

import io.ipfs.multihash.Multihash;


public class Types
{
	public static Multihash fromIpfsCid(String base58Cid)
	{
		return Multihash.fromBase58(base58Cid);
	}

	public static Multihash fromPublicKey(String hexKey)
	{
		return Multihash.fromHex(hexKey);
	}
}
