package com.jeffdisher.cacophony.logic;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.Version;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * Helpers related to generated JSON snippets for use in the REST interface.
 */
public class JsonGenerationHelpers
{
	public static JsonObject dataVersion()
	{
		return _dataVersion();
	}

	public static JsonArray postHashes(IReadingAccess access, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, IFolloweeReading followees, IpfsKey userToResolve) throws IpfsConnectionException, FailedDeserializationException
	{
		// We are only going to resolve this if it is this user or one we follow (at least for the near-term).
		IpfsFile indexToLoad = _getLastKnownIndexForKey(ourPublicKey, lastPublishedIndex, followees, userToResolve);
		JsonArray array = null;
		if (null != indexToLoad)
		{
			ForeignChannelReader reader = new ForeignChannelReader(access, indexToLoad, true);
			StreamRecords records = reader.loadRecords();
			array = new JsonArray();
			for (String rawCid : records.getRecord())
			{
				array.add(rawCid);
			}
		}
		return array;
	}

	/**
	 * Returns a JSON struct for the given postToResolve or null if it is unknown.
	 * NOTE:  This will only resolve stream elements this user posted or which was posted by a followee.
	 * 
	 * @param baseUrl The base URL to which CIDs can be appending to make a direct URL to the resource.
	 * @param cache The cache containing all the data we should be able to resolve.
	 * @param postToResolve The StreamRecord to resolve.
	 * @return The JSON representation of this post or null, if we don't know it.
	 */
	public static JsonObject postStruct(String baseUrl, LocalRecordCache cache, IpfsFile postToResolve)
	{
		LocalRecordCache.Element element = cache.get(postToResolve);
		return (null != element)
				? _formatAsPostStruct(baseUrl, element)
				: null
		;
	}

	public static JsonObject prefs(PrefsData prefs)
	{
		return _dataPrefs(prefs);
	}

	public  static JsonObject populateJsonForUnknownDescription(StreamDescription description, String userPicUrl)
	{
		return _populateJsonForDescription(description, userPicUrl);
	}

	public  static JsonObject populateJsonForCachedDescription(LocalUserInfoCache.Element element, String userPicUrl)
	{
		return _populateJsonForDataElements(element.name()
				, element.description()
				, userPicUrl
				, element.emailOrNull()
				, element.websiteOrNull()
		);
	}


	private static JsonObject _populateJsonForDescription(StreamDescription description, String userPicUrl)
	{
		return _populateJsonForDataElements(description.getName()
				, description.getDescription()
				, userPicUrl
				, description.getEmail()
				, description.getWebsite()
		);
	}

	private static JsonObject _populateJsonForDataElements(String name, String description, String userPicUrl, String emailOrNull, String websiteOrNull)
	{
		JsonObject thisUser = new JsonObject();
		thisUser.set("name", name);
		thisUser.set("description", description);
		thisUser.set("userPicUrl", userPicUrl);
		thisUser.set("email", emailOrNull);
		thisUser.set("website", websiteOrNull);
		return thisUser;
	}

	private static JsonObject _dataVersion()
	{
		JsonObject dataVersion = new JsonObject();
		dataVersion.set("hash", Version.HASH);
		dataVersion.set("version", Version.TAG);
		return dataVersion;
	}

	private static JsonObject _dataPrefs(PrefsData prefs)
	{
		JsonObject dataPrefs = new JsonObject();
		dataPrefs.set("edgeSize", prefs.videoEdgePixelMax);
		dataPrefs.set("followerCacheBytes", prefs.followCacheTargetBytes);
		dataPrefs.set("republishIntervalMillis", prefs.republishIntervalMillis);
		dataPrefs.set("followeeRefreshMillis", prefs.followeeRefreshMillis);
		return dataPrefs;
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

	private static JsonObject _formatAsPostStruct(String baseUrl, LocalRecordCache.Element element)
	{
		JsonObject thisElt = new JsonObject();
		thisElt.set("name", element.name());
		thisElt.set("description", element.description());
		thisElt.set("publishedSecondsUtc", element.publishedSecondsUtc());
		thisElt.set("discussionUrl", element.discussionUrl());
		thisElt.set("publisherKey", element.publisherKey());
		thisElt.set("cached", element.isCached());
		if (element.isCached())
		{
			thisElt.set("thumbnailUrl", _urlOrNull(baseUrl, element.thumbnailCid()));
			thisElt.set("videoUrl", _urlOrNull(baseUrl, element.videoCid()));
			thisElt.set("audioUrl", _urlOrNull(baseUrl, element.audioCid()));
		}
		return thisElt;
	}

	private static String _urlOrNull(String baseUrl, IpfsFile cid)
	{
		return (null != cid)
				? (baseUrl + cid.toSafeString())
				: null
		;
	}
}
