package com.jeffdisher.cacophony.commands;

import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChannelDescription;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.logic.HomeChannelModifier;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.CidOrNone;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.MiscHelpers;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * The command which updates the description elements of the local user's channel.
 * NOTE:  The _pictureStream, if not null, will be closed by this command, during runInEnvironment, so the caller can
 * relinguish ownership of it.
 */
public record UpdateDescriptionCommand(String _name, String _description, InputStream _pictureStream, String _email, String _website, CidOrNone _featurePost) implements ICommand<ChannelDescription>
{
	@Override
	public ChannelDescription runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		if (null == context.getSelectedKey())
		{
			throw new UsageException("Channel must first be created with --createNewChannel");
		}
		// All of the parameters are optional but at least one of them must be provided (null means "unchanged field").
		if ((null == _name) && (null == _description) && (null == _pictureStream) && (null == _email) && (null == _website) && (null == _featurePost))
		{
			throw new UsageException("At least one field must be being changed");
		}
		// These elements must also be non-empty.
		if ((null != _name) && _name.isEmpty())
		{
			throw new UsageException("Name must be non-empty.");
		}
		if ((null != _description) && _description.isEmpty())
		{
			throw new UsageException("Description must be non-empty.");
		}
		
		Result result;
		String pictureUrl;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			Assert.assertTrue(null != access.getLastRootElement());
			// Verify that this feature post is part of this user's stream.
			if ((null != _featurePost) && (null != _featurePost.cid))
			{
				try
				{
					AbstractIndex index = access.loadCached(access.getLastRootElement(), AbstractIndex.DESERIALIZER).get();
					AbstractRecords records = access.loadCached(index.recordsCid, AbstractRecords.DESERIALIZER).get();
					if (!records.getRecordList().contains(_featurePost.cid))
					{
						throw new UsageException("Feature post should be a record in your stream.");
					}
				}
				catch (FailedDeserializationException e)
				{
					// We can't fail to deserialize our own root.
					throw Assert.unexpected(e);
				}
				
			}
			ILogger log = context.logger.logStart("Updating channel description...");
			result = _run(access, _name, _description, _pictureStream, _email, _website, _featurePost);
			// We want to capture the picture URL while we still have access (whether or not we changed it).
			IpfsFile pictureCid = result.updatedStreamDescription().getPicCid();
			if (null != pictureCid)
			{
				Assert.assertTrue(access.isInPinCached(pictureCid));
				pictureUrl = context.baseUrl + pictureCid.toSafeString();
			}
			else
			{
				pictureUrl = null;
			}
			log.logFinish("Update completed!");
		}
		finally
		{
			// We took ownership of the stream so close it.
			if (null != _pictureStream)
			{
				try
				{
					_pictureStream.close();
				}
				catch (IOException e)
				{
					// We will just log and ignore this since it shouldn't happen but we would want to know about it.
					context.logger.logError(e.getLocalizedMessage());
				}
			}
		}
		AbstractDescription updated = result.updatedStreamDescription();
		context.cacheUpdater.userInfoCache_setUserInfo(context.getSelectedKey(), updated);
		return new ChannelDescription(result.newRoot()
				, updated.getName()
				, updated.getDescription()
				, updated.getPicCid()
				, updated.getEmail()
				, updated.getWebsite()
				, pictureUrl
				, updated.getFeature()
		);
	}

	/**
	 * Updates the local user's description.  Also unpins the old picture, if it is being replaced.
	 * 
	 * @param access Write access.
	 * @param name The new name (or null, if not changed).
	 * @param description The new description (or null, if not changed).
	 * @param picture The stream containing the new picture (or null, if not changed).
	 * @param email The E-Mail address (or null, if not changed).
	 * @param website The web site (or null, if not changed).
	 * @param featurePost The feature post CID (null, if not changed, resolving null if clearing).
	 * @return The results of the operation (not null).
	 * @throws IpfsConnectionException There was a network error.
	 * @throws UsageException The picture stream provided was too large.
	 */
	private static Result _run(IWritingAccess access
			, String name
			, String description
			, InputStream picture
			, String email
			, String website
			, CidOrNone featurePost
	) throws IpfsConnectionException, UsageException
	{
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		
		// Read the existing description since we might be only partially updating it.
		AbstractDescription descriptionObject = modifier.loadDescription();
		IpfsFile pictureToUnpin = null;
		
		if (null != name)
		{
			descriptionObject.setName(name);
		}
		if (null != description)
		{
			descriptionObject.setDescription(description);
		}
		
		if (null != picture)
		{
			// We don't want to pre-load this entire stream so try uploading it and then check the size to make sure it doesn't exceed the limit.
			IpfsFile pictureHash = access.uploadAndPin(picture);
			long sizeInBytes = access.getSizeInBytes(pictureHash).get();
			if (sizeInBytes > SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES)
			{
				access.unpin(pictureHash);
				throw new UsageException("Picture too big (is " + MiscHelpers.humanReadableBytes(sizeInBytes) + ", limit " + MiscHelpers.humanReadableBytes(SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES) + ")");
			}
			pictureToUnpin = descriptionObject.getPicCid();
			// TODO:  Plumb the mime in here.
			descriptionObject.setUserPic("image/jpeg", pictureHash);
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
		if (null != featurePost)
		{
			descriptionObject.setFeature(featurePost.cid);
		}
		
		// Update and commit the structure.
		modifier.storeDescription(descriptionObject);
		IpfsFile newRoot = modifier.commitNewRoot();
		
		// Clean up the old picture.
		if (null != pictureToUnpin)
		{
			access.unpin(pictureToUnpin);
		}
		
		return new Result(newRoot, descriptionObject);
	}


	public static record Result(IpfsFile newRoot, AbstractDescription updatedStreamDescription)
	{
	}
}
