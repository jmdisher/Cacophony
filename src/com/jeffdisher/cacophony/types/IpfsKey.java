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
public record IpfsKey(Multihash key)
{
	public static IpfsKey fromPublicKey(String keyAsString)
	{
		return new IpfsKey(Cid.decode(keyAsString));
	}
}
