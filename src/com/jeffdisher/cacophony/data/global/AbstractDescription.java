package com.jeffdisher.cacophony.data.global;

import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * Meant to abstract the differences between StreamDescriptions (V1) and CacophonyDescriptions (V2) while also providing
 * a higher-level interface.
 */
public class AbstractDescription
{
	public static final long SIZE_LIMIT_BYTES = SizeLimits.MAX_DESCRIPTION_SIZE_BYTES;
	public static final DataDeserializer<AbstractDescription> DESERIALIZER = (byte[] data) -> _convertRecord(GlobalData.deserializeDescription(data)); 

	/**
	 * @return A new empty record.
	 */
	public static AbstractDescription createNew()
	{
		return new AbstractDescription(null
				, null
				, null
				, null
				, null
				, null
		);
	}


	private static AbstractDescription _convertRecord(StreamDescription recordV1)
	{
		// We just assume that the MIME is JPEG.
		return new AbstractDescription(recordV1.getName()
				, recordV1.getDescription()
				, "image/jpeg"
				, IpfsFile.fromIpfsCid(recordV1.getPicture())
				, recordV1.getEmail()
				, recordV1.getWebsite()
		);
	}


	private String _name;
	private String _description;
	private String _picMime;
	private IpfsFile _picCid;
	private String _email;
	private String _website;

	private AbstractDescription(String name
			, String description
			, String picMime
			, IpfsFile picCid
			, String email
			, String website
	)
	{
		_name = name;
		_description = description;
		_picMime = picMime;
		_picCid = picCid;
		_email = email;
		_website = website;
	}

	/**
	 * @return The name of the user.
	 */
	public String getName()
	{
		return _name;
	}

	/**
	 * @param name The new name (cannot be null or empty).
	 */
	public void setName(String name)
	{
		Assert.assertTrue(!name.isEmpty());
		_name = name;
	}

	/**
	 * @return The description of the user.
	 */
	public String getDescription()
	{
		return _description;
	}

	/**
	 * @param description The new description (cannot be null).
	 */
	public void setDescription(String description)
	{
		Assert.assertTrue(null != description);
		_description = description;
	}

	/**
	 * @return The mime type of the user pic.
	 */
	public String getPicMime()
	{
		return _picMime;
	}

	/**
	 * @return The CID of the user pic.
	 */
	public IpfsFile getPicCid()
	{
		return _picCid;
	}

	/**
	 * Sets the parameters related to the user pic.
	 * 
	 * @param picMime The mime type of the user pic.
	 * @param picCid The new user pic CID.
	 */
	public void setUserPic(String picMime, IpfsFile picCid)
	{
		Assert.assertTrue(null != picMime);
		Assert.assertTrue(null != picCid);
		_picMime = picMime;
		_picCid = picCid;
	}

	public String getEmail()
	{
		return _email;
	}

	public void setEmail(String email)
	{
		Assert.assertTrue((null == email) || !email.isEmpty());
		_email = email;
	}

	public String getWebsite()
	{
		return _website;
	}

	public void setWebsite(String website)
	{
		Assert.assertTrue((null == website) || !website.isEmpty());
		_website = website;
	}

	/**
	 * Serializes the instance as a V1 StreamRecord, returning the resulting byte array.
	 * 
	 * @return The byte array of the serialized instance
	 * @throws SizeConstraintException The instance was too big to fit within limits, once serialized.
	 */
	public byte[] serializeV1() throws SizeConstraintException
	{
		StreamDescription description = new StreamDescription();
		Assert.assertTrue(_name.length() > 0);
		description.setName(_name);
		Assert.assertTrue(_description.length() > 0);
		description.setDescription(_description);
		Assert.assertTrue(null != _picMime);
		Assert.assertTrue(null != _picCid);
		description.setPicture(_picCid.toSafeString());
		if (null != _email)
		{
			Assert.assertTrue(_email.length() > 0);
			description.setEmail(_email);
		}
		if (null != _website)
		{
			Assert.assertTrue(_website.length() > 0);
			description.setWebsite(_website);
		}
		return GlobalData.serializeDescription(description);
	}
}
