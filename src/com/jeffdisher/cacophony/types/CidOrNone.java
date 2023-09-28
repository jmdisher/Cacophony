package com.jeffdisher.cacophony.types;


/**
 * A wrapper over IpfsFile in cases where it has a "null-like" value (generally meaning "clear the field").  This is for
 * cases where the value given can be a valid IPFS CID _OR_ the special constant "NONE".
 * The internal "cid" field is the CID or null, if the value was "NONE".
 */
public class CidOrNone
{
	public static final CidOrNone NONE = new CidOrNone();

	/**
	 * @param rawFeature The base-58 encoding of the IPFS hash, the string "NONE", or null.
	 * @return The CidOrNone or null if the rawFeature was invalid or null.
	 */
	public static CidOrNone parse(String rawFeature)
	{
		CidOrNone result;
		if (null != rawFeature)
		{
			if ("NONE".equals(rawFeature))
			{
				result = CidOrNone.NONE;
			}
			else
			{
				IpfsFile file = IpfsFile.fromIpfsCid(rawFeature);
				if (null != file)
				{
					result = new CidOrNone(file);
				}
				else
				{
					result = null;
				}
			}
		}
		else
		{
			result = null;
		}
		return result;
	}


	public final IpfsFile cid;

	private CidOrNone()
	{
		this.cid = null;
	}

	private CidOrNone(IpfsFile cid)
	{
		this.cid = cid;
	}
}
