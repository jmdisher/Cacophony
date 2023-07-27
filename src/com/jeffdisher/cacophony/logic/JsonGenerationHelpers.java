package com.jeffdisher.cacophony.logic;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.Version;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Helpers related to generated JSON snippets for use in the REST interface.
 */
public class JsonGenerationHelpers
{
	public static JsonObject dataVersion()
	{
		return _dataVersion();
	}

	public static JsonArray postHashes(IReadingAccess access, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, IFolloweeReading followees, IpfsKey userToResolve) throws IpfsConnectionException
	{
		// We are only going to resolve this if it is this user or one we follow (at least for the near-term).
		IpfsFile indexToLoad = _getLastKnownIndexForKey(ourPublicKey, lastPublishedIndex, followees, userToResolve);
		JsonArray array = null;
		if (null != indexToLoad)
		{
			ForeignChannelReader reader = new ForeignChannelReader(access, indexToLoad, true);
			AbstractRecords records;
			try
			{
				records = reader.loadRecords();
			}
			catch (ProtocolDataException e)
			{
				// We should not have already cached this if it was corrupt.
				throw Assert.unexpected(e);
			}
			array = new JsonArray();
			for (IpfsFile cid : records.getRecordList())
			{
				array.add(cid.toSafeString());
			}
		}
		return array;
	}

	public static JsonObject prefs(PrefsData prefs)
	{
		JsonObject dataPrefs = new JsonObject();
		dataPrefs.set("videoEdgePixelMax", prefs.videoEdgePixelMax);
		dataPrefs.set("followCacheTargetBytes", prefs.followCacheTargetBytes);
		dataPrefs.set("republishIntervalMillis", prefs.republishIntervalMillis);
		dataPrefs.set("followeeRefreshMillis", prefs.followeeRefreshMillis);
		dataPrefs.set("explicitCacheTargetBytes", prefs.explicitCacheTargetBytes);
		dataPrefs.set("followeeRecordThumbnailMaxBytes", prefs.followeeRecordThumbnailMaxBytes);
		dataPrefs.set("followeeRecordAudioMaxBytes", prefs.followeeRecordAudioMaxBytes);
		dataPrefs.set("followeeRecordVideoMaxBytes", prefs.followeeRecordVideoMaxBytes);
		return dataPrefs;
	}

	public static JsonObject userDescription(String name, String description, String userPicUrl, String emailOrNull, String websiteOrNull, IpfsFile featureOrNull)
	{
		JsonObject thisUser = new JsonObject();
		thisUser.set("name", name);
		thisUser.set("description", description);
		thisUser.set("userPicUrl", userPicUrl);
		thisUser.set("email", emailOrNull);
		thisUser.set("website", websiteOrNull);
		thisUser.set("feature", (null != featureOrNull) ? featureOrNull.toSafeString() : null);
		return thisUser;
	}


	private static JsonObject _dataVersion()
	{
		JsonObject dataVersion = new JsonObject();
		dataVersion.set("hash", Version.HASH);
		dataVersion.set("version", Version.TAG);
		return dataVersion;
	}

	private static IpfsFile _getLastKnownIndexForKey(IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, IFolloweeReading followees, IpfsKey userToResolve)
	{
		IpfsFile indexToLoad = null;
		if (userToResolve.equals(ourPublicKey))
		{
			indexToLoad = lastPublishedIndex;
		}
		else
		{
			indexToLoad = followees.getLastFetchedRootForFollowee(userToResolve);
		}
		return indexToLoad;
	}
}
