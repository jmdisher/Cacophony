package com.jeffdisher.cacophony.logic;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * Helpers related to generated JSON snippets for use in the REST interface.
 */
public class JsonGenerationHelpers
{
	/**
	 * Encodes the preferences as JSON.
	 * 
	 * @param prefs The prefs object.
	 * @return The prefs, encoded as JSON.
	 */
	public static JsonObject prefs(PrefsData prefs)
	{
		JsonObject dataPrefs = new JsonObject();
		dataPrefs.set("videoEdgePixelMax", prefs.videoEdgePixelMax);
		dataPrefs.set("republishIntervalMillis", prefs.republishIntervalMillis);
		dataPrefs.set("explicitCacheTargetBytes", prefs.explicitCacheTargetBytes);
		dataPrefs.set("explicitUserInfoRefreshMillis", prefs.explicitUserInfoRefreshMillis);
		dataPrefs.set("followeeCacheTargetBytes", prefs.followeeCacheTargetBytes);
		dataPrefs.set("followeeRefreshMillis", prefs.followeeRefreshMillis);
		dataPrefs.set("followeeRecordThumbnailMaxBytes", prefs.followeeRecordThumbnailMaxBytes);
		dataPrefs.set("followeeRecordAudioMaxBytes", prefs.followeeRecordAudioMaxBytes);
		dataPrefs.set("followeeRecordVideoMaxBytes", prefs.followeeRecordVideoMaxBytes);
		return dataPrefs;
	}

	/**
	 * Encodes the given user description information as JSON.
	 * 
	 * @param name The user name.
	 * @param description The user description.
	 * @param userPicUrl The user pic URL (could be null).
	 * @param emailOrNull The user's E-Mail address (could be null).
	 * @param websiteOrNull The user's website address (could be null).
	 * @param featureOrNull The user's feature post (could be null).
	 * @return The user data, encoded as JSON.
	 */
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
}
