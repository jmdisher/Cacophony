package com.jeffdisher.cacophony.types;

import com.jeffdisher.cacophony.utils.Assert;

import io.ipfs.multihash.Multihash;


/**
 * Since the Multihash object is used both for CID references and for public keys, this is a wrapper specifically for CIDs.
 */
public class IpfsFile
{
	/**
	 * @param base58Cid The base-58 encoding of the IPFS hash.
	 * @return The IpfsFile or null if the encoding was invalid.
	 */
	public static IpfsFile fromIpfsCid(String base58Cid)
	{
		Assert.assertTrue(null != base58Cid);
		IpfsFile file = null;
		try
		{
			file = new IpfsFile(Multihash.fromBase58(base58Cid));
		}
		catch (IllegalStateException e)
		{
			file = null;
		}
		return file;
	}


	private Multihash _cid;

	public IpfsFile(Multihash cid)
	{
		Assert.assertTrue(null != cid);
		_cid = cid;
	}

	public Multihash getMultihash()
	{
		return _cid;
	}

	public String toSafeString()
	{
		// Both toString and toBase58 give the same answer for underlying files (although they differ for keys).
		return _cid.toString();
	}

	@Override
	public boolean equals(Object obj)
	{
		boolean isEqual = false;
		if (obj instanceof IpfsFile)
		{
			isEqual = _cid.equals(((IpfsFile)obj)._cid);
		}
		return isEqual;
	}

	@Override
	public int hashCode()
	{
		return _cid.hashCode();
	}

	@Override
	public String toString()
	{
		return "IpfsFile(" + _cid.toString() + ")";
	}
}
