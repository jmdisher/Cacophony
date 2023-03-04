package com.jeffdisher.cacophony.logic;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.Version;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * Helpers related to generated JSON snippets for use in the REST interface.
 */
public class JsonGenerationHelpers
{
	public static void generateJsonDb(PrintWriter generatedStream, LocalRecordCache cache, String comment, IReadingAccess access, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, PrefsData prefs, IFolloweeReading followees) throws IpfsConnectionException, FailedDeserializationException
	{
		// Start output.
		generatedStream.println("// " + comment);
		generatedStream.println();
		
		// DATA_common.
		JsonObject dataCommon = new JsonObject();
		dataCommon.set("publicKey", ourPublicKey.toPublicKey());
		generatedStream.println("var DATA_common = " + dataCommon.toString());
		generatedStream.println();
		
		// DATA_version.
		JsonObject dataVersion = _dataVersion();
		generatedStream.println("var DATA_version = " + dataVersion.toString());
		generatedStream.println();
		
		// DATA_prefs.
		JsonObject dataPrefs = _dataPrefs(prefs);
		generatedStream.println("var DATA_prefs = " + dataPrefs.toString());
		generatedStream.println();
		
		// Load all the index objects since we walk it for a few operations.
		List<FutureKey<StreamIndex>> indices = _loadStreamIndices(access, ourPublicKey, lastPublishedIndex, followees);
		
		// DATA_userInfo.
		JsonObject dataUserInfo = _dataUserInfo(access, indices);
		generatedStream.println("var DATA_userInfo = " + dataUserInfo.toString());
		generatedStream.println();
		
		// DATA_elements.
		JsonObject dataElements = _dumpCacheToJson(cache);
		generatedStream.println("var DATA_elements = " + dataElements.toString());
		generatedStream.println();
		
		// DATA_userPosts.
		JsonObject dataUserPosts = new JsonObject();
		List<FutureKey<StreamRecords>> streamRecords = _loadRecords(access, indices);
		
		for (FutureKey<StreamRecords> elt : streamRecords)
		{
			_populatePostsForUser(dataUserPosts, elt.publicKey(), elt.future().get());
		}
		generatedStream.println("var DATA_userPosts = " + dataUserPosts.toString());
		generatedStream.println();
		
		// DATA_recommended.
		// Load recommended list.
		List<FutureKey<StreamRecommendations>> recommendationsFutures = _loadRecommendations(access, indices);
		JsonObject dataRecommended = new JsonObject();
		for (FutureKey<StreamRecommendations> elt : recommendationsFutures)
		{
			_populateRecommendationsForUser(dataRecommended, elt.publicKey(), elt.future().get());
		}
		generatedStream.println("var DATA_recommended = " + dataRecommended.toString());
		generatedStream.println();
		
		// DATA_following.
		JsonArray dataFollowing = _dataFollowing(followees);
		generatedStream.println("var DATA_following = " + dataFollowing.toString());
		generatedStream.println();
		
		// Drop in the API wrappers - this will be what allows the output mode and the interactive mode to diverge, with the same API.
		generatedStream.println(""
			+ "function API_loadPublicKey()\n"
			+ "{\n"
			+ "	return new Promise(resolve => {\n"
			+ "		setTimeout(() => {\n"
			+ "			resolve(DATA_common[\"publicKey\"]);\n"
			+ "		});\n"
			+ "	});\n"
			+ "}\n"
			+ "\n"
			+ "function API_getInfoForUser(publicKey)\n"
			+ "{\n"
			+ "	return new Promise(resolve => {\n"
			+ "		setTimeout(() => {\n"
			+ "			resolve(DATA_userInfo[publicKey]);\n"
			+ "		});\n"
			+ "	});\n"
			+ "}\n"
			+ "\n"
			+ "function API_getAllPostsForUser(publicKey)\n"
			+ "{\n"
			+ "	return new Promise(resolve => {\n"
			+ "		setTimeout(() => {\n"
			+ "			resolve(DATA_userPosts[publicKey]);\n"
			+ "		});\n"
			+ "	});\n"
			+ "}\n"
			+ "\n"
			+ "function API_getRecommendedUsers(publicKey)\n"
			+ "{\n"
			+ "	return new Promise(resolve => {\n"
			+ "		setTimeout(() => {\n"
			+ "			resolve(DATA_recommended[publicKey]);\n"
			+ "		});\n"
			+ "	});\n"
			+ "}\n"
			+ "\n"
			+ "function API_getPost(hash)\n"
			+ "{\n"
			+ "	return new Promise(resolve => {\n"
			+ "		setTimeout(() => {\n"
			+ "			resolve(DATA_elements[hash]);\n"
			+ "		});\n"
			+ "	});\n"
			+ "}\n"
			+ "\n"
			+ "function API_getFollowedKeys()\n"
			+ "{\n"
			+ "	return new Promise(resolve => {\n"
			+ "		setTimeout(() => {\n"
			+ "			resolve(DATA_following);\n"
			+ "		});\n"
			+ "	});\n"
			+ "}\n"
			+ "\n"
			+ "function API_getPrefs()\n"
			+ "{\n"
			+ "	return new Promise(resolve => {\n"
			+ "		setTimeout(() => {\n"
			+ "			resolve(DATA_prefs);\n"
			+ "		});\n"
			+ "	});\n"
			+ "}\n"
			+ "\n"
			+ "function API_getVersion()\n"
			+ "{\n"
			+ "	return new Promise(resolve => {\n"
			+ "		setTimeout(() => {\n"
			+ "			resolve(DATA_version);\n"
			+ "		});\n"
			+ "	});\n"
			+ "}\n"
			+ "\n"
			+ "function API_getXsrf()\n"
			+ "{\n"
			+ "	return new Promise(resolve => {\n"
			+ "		setTimeout(() => {\n"
			+ "			resolve();\n"
			+ "		});\n"
			+ "	});\n"
			+ "}\n"
		);
		
		generatedStream.close();
	}

	public static JsonObject dataVersion()
	{
		return _dataVersion();
	}

	public static JsonObject userInfo(IReadingAccess access, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, IFolloweeReading followees, IpfsKey userToResolve) throws IpfsConnectionException, FailedDeserializationException
	{
		// We are only going to resolve this if it is this user or one we follow (at least for the near-term).
		IpfsFile indexToLoad = _getLastKnownIndexForKey(ourPublicKey, lastPublishedIndex, followees, userToResolve);
		JsonObject foundObject = null;
		if (null != indexToLoad)
		{
			StreamIndex index = access.loadCached(indexToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
			StreamDescription description = access.loadCached(IpfsFile.fromIpfsCid(index.getDescription()), (byte[] data) -> GlobalData.deserializeDescription(data)).get();
			foundObject = _populateJsonForDescription(description, access.getCachedUrl(IpfsFile.fromIpfsCid(description.getPicture())).toString());
		}
		return foundObject;
	}

	public static JsonArray postHashes(IReadingAccess access, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, IFolloweeReading followees, IpfsKey userToResolve) throws IpfsConnectionException, FailedDeserializationException
	{
		// We are only going to resolve this if it is this user or one we follow (at least for the near-term).
		IpfsFile indexToLoad = _getLastKnownIndexForKey(ourPublicKey, lastPublishedIndex, followees, userToResolve);
		JsonArray array = null;
		if (null != indexToLoad)
		{
			StreamIndex index = access.loadCached(indexToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
			StreamRecords records = access.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data)).get();
			array = new JsonArray();
			for (String rawCid : records.getRecord())
			{
				array.add(rawCid);
			}
		}
		return array;
	}

	public static JsonArray recommendedKeys(IReadingAccess access, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, IFolloweeReading followees, IpfsKey userToResolve) throws IpfsConnectionException, FailedDeserializationException
	{
		// We are only going to resolve this if it is this user or one we follow (at least for the near-term).
		IpfsFile indexToLoad = _getLastKnownIndexForKey(ourPublicKey, lastPublishedIndex, followees, userToResolve);
		JsonArray array = null;
		if (null != indexToLoad)
		{
			StreamIndex index = access.loadCached(indexToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
			StreamRecommendations recommendations = access.loadCached(IpfsFile.fromIpfsCid(index.getRecommendations()), (byte[] data) -> GlobalData.deserializeRecommendations(data)).get();
			array = new JsonArray();
			for (String rawCid : recommendations.getUser())
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
	 * @param cache The cache containing all the data we should be able to resolve.
	 * @param postToResolve The StreamRecord to resolve.
	 * @return The JSON representation of this post or null, if we don't know it.
	 */
	public static JsonObject postStruct(LocalRecordCache cache, IpfsFile postToResolve)
	{
		LocalRecordCache.Element element = cache.get(postToResolve);
		return (null != element)
				? _formatAsPostStruct(element)
				: null
		;
	}

	public static JsonArray followeeKeys(IFolloweeReading followees)
	{
		return _dataFollowing(followees);
	}

	public static JsonObject prefs(PrefsData prefs)
	{
		return _dataPrefs(prefs);
	}

	public  static JsonObject populateJsonForUnknownDescription(StreamDescription description, String userPicUrl)
	{
		return _populateJsonForDescription(description, userPicUrl);
	}


	private static FutureKey<StreamIndex> _startLoad(IReadingAccess access, IpfsKey publicKey, IpfsFile indexRoot)
	{
		FutureRead<StreamIndex> index = access.loadCached(indexRoot, (byte[] data) -> GlobalData.deserializeIndex(data));
		return new FutureKey<StreamIndex>(publicKey, index);
	}

	private static List<FutureKey<StreamDescription>> _loadDescriptions(IReadingAccess access, List<FutureKey<StreamIndex>> list) throws IpfsConnectionException, FailedDeserializationException
	{
		List<FutureKey<StreamDescription>> descriptions = new ArrayList<>();
		for (FutureKey<StreamIndex> future : list)
		{
			StreamIndex index = future.future.get();
			FutureRead<StreamDescription> description = access.loadCached(IpfsFile.fromIpfsCid(index.getDescription()), (byte[] data) -> GlobalData.deserializeDescription(data));
			descriptions.add(new FutureKey<StreamDescription>(future.publicKey, description));
		}
		return descriptions;
	}

	private static JsonObject _populateJsonForDescription(StreamDescription description, String userPicUrl)
	{
		JsonObject thisUser = new JsonObject();
		thisUser.set("name", description.getName());
		thisUser.set("description", description.getDescription());
		thisUser.set("userPicUrl", userPicUrl);
		thisUser.set("email", description.getEmail());
		thisUser.set("website", description.getWebsite());
		return thisUser;
	}

	private static List<FutureKey<StreamRecords>> _loadRecords(IReadingAccess access, List<FutureKey<StreamIndex>> list) throws IpfsConnectionException, FailedDeserializationException
	{
		List<FutureKey<StreamRecords>> recordsList = new ArrayList<>();
		for (FutureKey<StreamIndex> future : list)
		{
			StreamIndex index = future.future.get();
			FutureRead<StreamRecords> records = access.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data));
			recordsList.add(new FutureKey<StreamRecords>(future.publicKey, records));
		}
		return recordsList;
	}

	private static JsonObject _dumpCacheToJson(LocalRecordCache cache)
	{
		JsonObject object = new JsonObject();
		Set<IpfsFile> elements = cache.getKeys();
		for (IpfsFile file : elements)
		{
			LocalRecordCache.Element element = cache.get(file);
			JsonObject thisElt = _formatAsPostStruct(element);
			object.set(file.toSafeString(), thisElt);
		}
		return object;
	}

	private static void _populatePostsForUser(JsonObject rootData, IpfsKey publicKey, StreamRecords records)
	{
		JsonArray array = new JsonArray();
		for (String rawCid : records.getRecord())
		{
			array.add(rawCid);
		}
		rootData.set(publicKey.toPublicKey(), array);
	}

	private static List<FutureKey<StreamRecommendations>> _loadRecommendations(IReadingAccess access, List<FutureKey<StreamIndex>> list) throws IpfsConnectionException, FailedDeserializationException
	{
		List<FutureKey<StreamRecommendations>> recommendationsList = new ArrayList<>();
		for (FutureKey<StreamIndex> future : list)
		{
			StreamIndex index = future.future.get();
			FutureRead<StreamRecommendations> recommendations = access.loadCached(IpfsFile.fromIpfsCid(index.getRecommendations()), (byte[] data) -> GlobalData.deserializeRecommendations(data));
			recommendationsList.add(new FutureKey<StreamRecommendations>(future.publicKey, recommendations));
		}
		return recommendationsList;
	}

	private static void _populateRecommendationsForUser(JsonObject rootData, IpfsKey publicKey,StreamRecommendations recommendations)
	{
		JsonArray array = new JsonArray();
		for (String rawCid : recommendations.getUser())
		{
			array.add(rawCid);
		}
		rootData.set(publicKey.toPublicKey(), array);
	}

	private static List<FutureKey<StreamIndex>> _loadStreamIndices(IReadingAccess access, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, IFolloweeReading followees)
	{
		List<FutureKey<StreamIndex>> indices = new ArrayList<>();
		indices.add(_startLoad(access, ourPublicKey, lastPublishedIndex));
		for(IpfsKey followee : followees.getAllKnownFollowees())
		{
			indices.add(_startLoad(access, followee, followees.getLastFetchedRootForFollowee(followee)));
		}
		return indices;
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

	private static JsonObject _dataUserInfo(IReadingAccess access, List<FutureKey<StreamIndex>> indices) throws IpfsConnectionException, FailedDeserializationException
	{
		JsonObject dataUserInfo = new JsonObject();
		List<FutureKey<StreamDescription>> descriptions = _loadDescriptions(access, indices);
		for (FutureKey<StreamDescription> future : descriptions)
		{
			StreamDescription streamDescription = future.future.get();
			JsonObject json = _populateJsonForDescription(streamDescription, access.getCachedUrl(IpfsFile.fromIpfsCid(streamDescription.getPicture())).toString());
			dataUserInfo.set(future.publicKey.toPublicKey(), json);
		}
		return dataUserInfo;
	}

	private static JsonArray _dataFollowing(IFolloweeReading followees)
	{
		JsonArray dataFollowing = new JsonArray();
		for(IpfsKey followee: followees.getAllKnownFollowees())
		{
			dataFollowing.add(followee.toPublicKey());
		}
		return dataFollowing;
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

	private static JsonObject _formatAsPostStruct(LocalRecordCache.Element element)
	{
		JsonObject thisElt = new JsonObject();
		thisElt.set("name", element.name());
		thisElt.set("description", element.description());
		thisElt.set("publishedSecondsUtc", element.publishedSecondsUtc());
		thisElt.set("discussionUrl", element.discussionUrl());
		thisElt.set("cached", element.isCached());
		if (element.isCached())
		{
			thisElt.set("thumbnailUrl", element.thumbnailUrl());
			thisElt.set("videoUrl", element.videoUrl());
			thisElt.set("audioUrl", element.audioUrl());
		}
		return thisElt;
	}


	private static record FutureKey<T>(IpfsKey publicKey, FutureRead<T> future)
	{
	}
}
