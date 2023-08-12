package com.jeffdisher.cacophony.logic;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.Version;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * Helpers related to generated JSON snippets for use in the REST interface.
 */
public class JsonGenerationHelpers
{
	public static JsonObject dataVersion()
	{
		return _dataVersion();
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
}
