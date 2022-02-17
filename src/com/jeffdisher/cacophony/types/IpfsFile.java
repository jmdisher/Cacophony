package com.jeffdisher.cacophony.types;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import com.jeffdisher.cacophony.utils.Assert;

import io.ipfs.multihash.Multihash;


/**
 * Since the Multihash object is used both for CID references and for public keys, this is a wrapper specifically for CIDs.
 */
public class IpfsFile implements Serializable
{
	private static final long serialVersionUID = 1L;
	public static IpfsFile fromIpfsCid(String base58Cid)
	{
		Assert.assertTrue(null != base58Cid);
		return new IpfsFile(Multihash.fromBase58(base58Cid));
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

	private void readObject(ObjectInputStream inputStream) throws ClassNotFoundException, IOException {
		_cid = Multihash.fromBase58(inputStream.readUTF());
	}

	private void writeObject(ObjectOutputStream outputStream) throws IOException {
		outputStream.writeUTF(_cid.toBase58());
	}
}
