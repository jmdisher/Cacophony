package com.jeffdisher.cacophony.data.global;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.v2.description.CacophonyDescription;
import com.jeffdisher.cacophony.data.global.v2.description.MiscData;
import com.jeffdisher.cacophony.data.global.v2.description.PictureReference;
import com.jeffdisher.cacophony.types.DataDeserializer;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
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
	/**
	 * The maximum size, in bytes, of a description file.
	 */
	public static final long SIZE_LIMIT_BYTES = SizeLimits.MAX_DESCRIPTION_SIZE_BYTES;
	/**
	 * The shared deserializer for reading an instance from raw data.
	 */
	public static final DataDeserializer<AbstractDescription> DESERIALIZER = (byte[] data) -> _commonMultiVersionLoad(data);

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
				, null
		);
	}


	private static AbstractDescription _commonMultiVersionLoad(byte[] data) throws FailedDeserializationException
	{
		AbstractDescription converted;
		try
		{
			// We check for version 2, first.
			CacophonyDescription recordsV2 = GlobalData2.deserializeDescription(data);
			converted = _convertRecordV2(recordsV2);
		}
		catch (FailedDeserializationException e)
		{
			// We will try version 1.
			StreamDescription recordsV1 = GlobalData.deserializeDescription(data);
			converted = _convertRecordV1(recordsV1);
		}
		
		// We would have loaded one of them or thrown.
		return converted;
	}

	private static AbstractDescription _convertRecordV2(CacophonyDescription descriptionV2)
	{
		PictureReference ref = descriptionV2.getPicture();
		Map<String, String> misc = descriptionV2.getMisc().stream().collect(Collectors.toMap(
				MiscData::getType, MiscData::getValue
		));
		String rawFeature = descriptionV2.getFeature();
		return new AbstractDescription(descriptionV2.getName()
				, descriptionV2.getDescription()
				, (null != ref) ? ref.getMime() : null
				, (null != ref) ? IpfsFile.fromIpfsCid(ref.getValue()) : null
				, misc.get(GlobalData2.DESCRIPTION_MISC_TYPE_EMAIL)
				, misc.get(GlobalData2.DESCRIPTION_MISC_TYPE_WEBSITE)
				, (null != rawFeature) ? IpfsFile.fromIpfsCid(rawFeature) : null
		);
	}

	private static AbstractDescription _convertRecordV1(StreamDescription recordV1)
	{
		// We just assume that the MIME is JPEG.
		return new AbstractDescription(recordV1.getName()
				, recordV1.getDescription()
				, "image/jpeg"
				, IpfsFile.fromIpfsCid(recordV1.getPicture())
				, recordV1.getEmail()
				, recordV1.getWebsite()
				, null
		);
	}


	private String _name;
	private String _description;
	private String _picMime;
	private IpfsFile _picCid;
	private String _email;
	private String _website;
	private IpfsFile _feature;

	private AbstractDescription(String name
			, String description
			, String picMime
			, IpfsFile picCid
			, String email
			, String website
			, IpfsFile feature
	)
	{
		_name = name;
		_description = description;
		_picMime = picMime;
		_picCid = picCid;
		_email = email;
		_website = website;
		_feature = feature;
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

	/**
	 * @return The user's E-Mail address (could be null).
	 */
	public String getEmail()
	{
		return _email;
	}

	/**
	 * Sets the user's E-Mail.
	 * 
	 * @param email The new E-Mail address (can be null but never empty).
	 */
	public void setEmail(String email)
	{
		Assert.assertTrue((null == email) || !email.isEmpty());
		_email = email;
	}

	/**
	 * @return The user's website/URL (could be null).
	 */
	public String getWebsite()
	{
		return _website;
	}

	/**
	 * Sets the user's website/URL.
	 * 
	 * @param website The new website URL for the user (can be null but never empty).
	 */
	public void setWebsite(String website)
	{
		Assert.assertTrue((null == website) || !website.isEmpty());
		_website = website;
	}

	/**
	 * @return The user's feature post CID (could be null).
	 */
	public IpfsFile getFeature()
	{
		return _feature;
	}

	/**
	 * Sets the user's feature post CID.
	 * 
	 * @param feature The new feature post CID for the user (can be null to clear it).
	 */
	public void setFeature(IpfsFile feature)
	{
		_feature = feature;
	}

	/**
	 * Serializes the instance as a V1 StreamDescription, returning the resulting byte array.
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
		// We can't be using a feature if serializing V1.
		Assert.assertTrue(null == _feature);
		return GlobalData.serializeDescription(description);
	}

	/**
	 * Serializes the instance as a V2 CacophonyDescription, returning the resulting byte array.
	 * 
	 * @return The byte array of the serialized instance
	 * @throws SizeConstraintException The instance was too big to fit within limits, once serialized.
	 */
	public byte[] serializeV2() throws SizeConstraintException
	{
		CacophonyDescription description = new CacophonyDescription();
		Assert.assertTrue(_name.length() > 0);
		description.setName(_name);
		Assert.assertTrue(_description.length() > 0);
		description.setDescription(_description);
		if (null != _picCid)
		{
			Assert.assertTrue(null != _picMime);
			PictureReference ref = new PictureReference();
			ref.setMime(_picMime);
			ref.setValue(_picCid.toSafeString());
			description.setPicture(ref);
		}
		List<MiscData> misc = description.getMisc();
		if (null != _email)
		{
			Assert.assertTrue(_email.length() > 0);
			MiscData data = new MiscData();
			data.setType(GlobalData2.DESCRIPTION_MISC_TYPE_EMAIL);
			data.setValue(_email);
			misc.add(data);
		}
		if (null != _website)
		{
			Assert.assertTrue(_website.length() > 0);
			MiscData data = new MiscData();
			data.setType(GlobalData2.DESCRIPTION_MISC_TYPE_WEBSITE);
			data.setValue(_website);
			misc.add(data);
		}
		if (null != _feature)
		{
			description.setFeature(_feature.toSafeString());
		}
		return GlobalData2.serializeDescription(description);
	}
}
