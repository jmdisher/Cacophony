package com.jeffdisher.cacophony.actions;

import java.io.InputStream;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.logic.ChannelModifier;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.MiscHelpers;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * The common logic for the different paths to update the user's description.
 */
public class UpdateDescription
{
	/**
	 * Updates the local user's description.  Also unpins the old picture, if it is being replaced.
	 * 
	 * @param access Write access.
	 * @param name The new name (or null, if not changed).
	 * @param description The new description (or null, if not changed).
	 * @param picture The stream containing the new picture (or null, if not changed).
	 * @param email The E-Mail address (or null, if not changed).
	 * @param website The web site (or null, if not changed).
	 * @return The results of the operation (not null).
	 * @throws IpfsConnectionException There was a network error.
	 * @throws UsageException The picture stream provided was too large.
	 */
	public static Result run(IWritingAccess access
			, String name
			, String description
			, InputStream picture
			, String email
			, String website
	) throws IpfsConnectionException, UsageException
	{
		ChannelModifier modifier = new ChannelModifier(access);
		
		// Read the existing description since we might be only partially updating it.
		StreamDescription descriptionObject = ActionHelpers.readDescription(modifier);
		IpfsFile pictureToUnpin = null;
		
		if (null != name)
		{
			descriptionObject.setName(name);
		}
		if (null != description)
		{
			descriptionObject.setDescription(description);
		}
		IpfsFile pictureHash = null;
		if (null != picture)
		{
			// We don't want to pre-load this entire stream so try uploading it and then check the size to make sure it doesn't exceed the limit.
			pictureHash = access.uploadAndPin(picture);
			long sizeInBytes = access.getSizeInBytes(pictureHash).get();
			if (sizeInBytes > SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES)
			{
				access.unpin(pictureHash);
				throw new UsageException("Picture too big (is " + MiscHelpers.humanReadableBytes(sizeInBytes) + ", limit " + MiscHelpers.humanReadableBytes(SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES) + ")");
			}
			pictureToUnpin = IpfsFile.fromIpfsCid(descriptionObject.getPicture());
			descriptionObject.setPicture(pictureHash.toSafeString());
		}
		if (null != email)
		{
			// Since email is optional, we will treat an empty string as "remove".
			if (email.isEmpty())
			{
				descriptionObject.setEmail(null);
			}
			else
			{
				descriptionObject.setEmail(email);
			}
		}
		if (null != website)
		{
			// Since website is optional, we will treat an empty string as "remove".
			if (website.isEmpty())
			{
				descriptionObject.setWebsite(null);
			}
			else
			{
				descriptionObject.setWebsite(website);
			}
		}
		
		// Update and commit the structure.
		modifier.storeDescription(descriptionObject);
		IpfsFile newRoot = ActionHelpers.commitNewRoot(modifier);
		
		// Clean up the old picture.
		if (null != pictureToUnpin)
		{
			access.unpin(pictureToUnpin);
		}
		
		return new Result(newRoot, descriptionObject, pictureHash);
	}


	public static record Result(IpfsFile newRoot, StreamDescription updatedStreamDescription, IpfsFile uploadedPictureCid)
	{
	}
}
