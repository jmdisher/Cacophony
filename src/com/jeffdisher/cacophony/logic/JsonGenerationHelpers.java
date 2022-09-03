package com.jeffdisher.cacophony.logic;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.Version;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Helpers related to generated JSON snippets for use in the REST interface.
 */
public class JsonGenerationHelpers
{
	public static void generateJsonDb(PrintWriter generatedStream, String comment, LoadChecker checker, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, GlobalPrefs prefs, FollowIndex followIndex) throws IpfsConnectionException
	{
		generatedStream.println("// " + comment);
		generatedStream.println();
		
		// DATA_common.
		JsonObject dataCommon = new JsonObject();
		dataCommon.set("publicKey", ourPublicKey.toPublicKey());
		generatedStream.println("var DATA_common = " + dataCommon.toString());
		generatedStream.println();
		
		// DATA_version.
		JsonObject dataVersion = new JsonObject();
		dataVersion.set("hash", Version.HASH);
		dataVersion.set("version", Version.TAG);
		generatedStream.println("var DATA_version = " + dataVersion.toString());
		generatedStream.println();
		
		// DATA_prefs.
		JsonObject dataPrefs = new JsonObject();
		dataPrefs.set("edgeSize", prefs.videoEdgePixelMax());
		dataPrefs.set("followerCacheBytes", prefs.followCacheTargetBytes());
		generatedStream.println("var DATA_prefs = " + dataPrefs.toString());
		generatedStream.println();
		
		// Load all the index objects since we walk it for a few operations.
		List<FutureKey<StreamIndex>> indices = _loadStreamIndices(checker, ourPublicKey, lastPublishedIndex, followIndex);
		
		// DATA_userInfo.
		JsonObject dataUserInfo = _dataUserInfo(checker, indices);
		generatedStream.println("var DATA_userInfo = " + dataUserInfo.toString());
		generatedStream.println();
		
		// DATA_elements.
		JsonObject dataElements = new JsonObject();
		// Fetch the StreamRecords.
		List<FutureKey<StreamRecords>> streamRecords = _loadRecords(checker, indices);
		
		// Note that the elements in streamRecords are derived from ourselves and all FollowIndex elements, so we can walk them in the same order.
		Iterator<FutureKey<StreamRecords>> recordsIterator = streamRecords.iterator();
		// The first element is ourselves.
		_populateAllElementsFromUserRoot(checker, dataElements, null, recordsIterator.next().future.get());
		// The rest of the list is in-order with followIndex.
		for(FollowRecord record : followIndex)
		{
			Map<IpfsFile, FollowingCacheElement> elementsCachedForUser = Arrays.stream(record.elements()).collect(Collectors.toMap((e) -> e.elementHash(), (e) -> e));
			_populateAllElementsFromUserRoot(checker, dataElements, elementsCachedForUser, recordsIterator.next().future.get());
		}
		// These should end at the same time.
		Assert.assertTrue(!recordsIterator.hasNext());
		generatedStream.println("var DATA_elements = " + dataElements.toString());
		generatedStream.println();
		
		// DATA_userPosts.
		JsonObject dataUserPosts = new JsonObject();
		recordsIterator = streamRecords.iterator();
		_populatePostsForUser(checker, dataUserPosts, ourPublicKey, recordsIterator.next().future.get());
		for(FollowRecord record : followIndex)
		{
			_populatePostsForUser(checker, dataUserPosts, record.publicKey(), recordsIterator.next().future.get());
		}
		Assert.assertTrue(!recordsIterator.hasNext());
		generatedStream.println("var DATA_userPosts = " + dataUserPosts.toString());
		generatedStream.println();
		
		// DATA_recommended.
		// Load recommended list.
		List<FutureKey<StreamRecommendations>> recommendationsFutures = _loadRecommendations(checker, indices);
		JsonObject dataRecommended = new JsonObject();
		Iterator<FutureKey<StreamRecommendations>> recommendationsIterator = recommendationsFutures.iterator();
		_populateRecommendationsForUser(checker, dataRecommended, ourPublicKey, recommendationsIterator.next().future.get());
		for(FollowRecord record : followIndex)
		{
			_populateRecommendationsForUser(checker, dataRecommended, record.publicKey(), recommendationsIterator.next().future.get());
		}
		Assert.assertTrue(!recommendationsIterator.hasNext());
		generatedStream.println("var DATA_recommended = " + dataRecommended.toString());
		generatedStream.println();
		
		// DATA_following.
		JsonArray dataFollowing = _dataFollowing(followIndex);
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
		);
		
		generatedStream.close();
	}

	public static JsonObject userInfo(LoadChecker checker, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, FollowIndex followIndex, IpfsKey userToResolve) throws IpfsConnectionException
	{
		// We are only going to resolve this if it is this user or one we follow (at least for the near-term).
		IpfsFile indexToLoad = _getLastKnownIndexForKey(ourPublicKey, lastPublishedIndex, followIndex, userToResolve);
		JsonObject foundObject = null;
		if (null != indexToLoad)
		{
			StreamIndex index = checker.loadCached(indexToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
			StreamDescription description = checker.loadCached(IpfsFile.fromIpfsCid(index.getDescription()), (byte[] data) -> GlobalData.deserializeDescription(data)).get();
			foundObject = _populateJsonForDescription(checker, description);
		}
		return foundObject;
	}

	public static JsonArray postHashes(LoadChecker checker, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, FollowIndex followIndex, IpfsKey userToResolve) throws IpfsConnectionException
	{
		// We are only going to resolve this if it is this user or one we follow (at least for the near-term).
		IpfsFile indexToLoad = _getLastKnownIndexForKey(ourPublicKey, lastPublishedIndex, followIndex, userToResolve);
		JsonArray array = null;
		if (null != indexToLoad)
		{
			StreamIndex index = checker.loadCached(indexToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
			StreamRecords records = checker.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data)).get();
			array = new JsonArray();
			for (String rawCid : records.getRecord())
			{
				array.add(rawCid);
			}
		}
		return array;
	}

	public static JsonArray recommendedKeys(LoadChecker checker, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, FollowIndex followIndex, IpfsKey userToResolve) throws IpfsConnectionException
	{
		// We are only going to resolve this if it is this user or one we follow (at least for the near-term).
		IpfsFile indexToLoad = _getLastKnownIndexForKey(ourPublicKey, lastPublishedIndex, followIndex, userToResolve);
		JsonArray array = null;
		if (null != indexToLoad)
		{
			StreamIndex index = checker.loadCached(indexToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
			StreamRecommendations recommendations = checker.loadCached(IpfsFile.fromIpfsCid(index.getRecommendations()), (byte[] data) -> GlobalData.deserializeRecommendations(data)).get();
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
	 * @param checker
	 * @param lastPublishedIndex
	 * @param followIndex
	 * @param postToResolve
	 * @return
	 * @throws IpfsConnectionException
	 */
	public static JsonObject postStruct(LoadChecker checker, IpfsFile lastPublishedIndex, FollowIndex followIndex, IpfsFile postToResolve) throws IpfsConnectionException
	{
		JsonObject foundObject = null;
		// TODO:  Currently, we don't store any information around what we have cached locally in any way which can be
		// indexed for one-off lookups.  This MUST be fixed (even with a basic in-memory cache) as our current approach
		// walks all the data to find this, including loading some data from the node in order to find the element and
		// know how to interpret it.
		
		// First, scan our own data.
		foundObject = _findElementFromRoot(checker, lastPublishedIndex, null, postToResolve);
		if (null == foundObject)
		{
			// We didn't find it so take a look at the users we are following.
			for(FollowRecord record : followIndex)
			{
				Map<IpfsFile, FollowingCacheElement> elementsCachedForUser = Arrays.stream(record.elements()).collect(Collectors.toMap((e) -> e.elementHash(), (e) -> e));
				foundObject = _findElementFromRoot(checker, lastPublishedIndex, elementsCachedForUser, postToResolve);
				if (null != foundObject)
				{
					break;
				}
			}
		}
		return foundObject;
	}

	public static JsonArray followeeKeys(FollowIndex followIndex)
	{
		return _dataFollowing(followIndex);
	}


	private static JsonObject _findElementFromRoot(LoadChecker checker, IpfsFile lastPublishedIndex, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, IpfsFile postToResolve) throws IpfsConnectionException
	{
		JsonObject foundObject = null;
		StreamIndex index = checker.loadCached(lastPublishedIndex, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		StreamRecords records = checker.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data)).get();
		for (String rawCid : records.getRecord())
		{
			if (postToResolve.equals(IpfsFile.fromIpfsCid(rawCid)))
			{
				FutureRead<StreamRecord> futureRecord = checker.loadCached(postToResolve, (byte[] data) -> GlobalData.deserializeRecord(data));
				foundObject = _loadSinglePostStruct(checker, elementsCachedForUser, futureRecord, postToResolve);
				break;
			}
		}
		return foundObject;
	}

	private static void _startLoad(List<FutureKey<StreamIndex>> list, LoadChecker checker, IpfsKey publicKey, IpfsFile indexRoot)
	{
		FutureRead<StreamIndex> index = checker.loadCached(indexRoot, (byte[] data) -> GlobalData.deserializeIndex(data));
		list.add(new FutureKey<StreamIndex>(publicKey, index));
	}

	private static List<FutureKey<StreamDescription>> _loadDescriptions(LoadChecker checker, List<FutureKey<StreamIndex>> list) throws IpfsConnectionException
	{
		List<FutureKey<StreamDescription>> descriptions = new ArrayList<>();
		for (FutureKey<StreamIndex> future : list)
		{
			StreamIndex index = future.future.get();
			FutureRead<StreamDescription> description = checker.loadCached(IpfsFile.fromIpfsCid(index.getDescription()), (byte[] data) -> GlobalData.deserializeDescription(data));
			descriptions.add(new FutureKey<StreamDescription>(future.publicKey, description));
		}
		return descriptions;
	}

	private static JsonObject _populateJsonForDescription(LoadChecker checker, StreamDescription description)
	{
		JsonObject thisUser = new JsonObject();
		thisUser.set("name", description.getName());
		thisUser.set("description", description.getDescription());
		thisUser.set("userPicUrl", checker.getCachedUrl(IpfsFile.fromIpfsCid(description.getPicture())).toString());
		thisUser.set("email", description.getEmail());
		thisUser.set("website", description.getWebsite());
		return thisUser;
	}

	private static List<FutureKey<StreamRecords>> _loadRecords(LoadChecker checker, List<FutureKey<StreamIndex>> list) throws IpfsConnectionException
	{
		List<FutureKey<StreamRecords>> recordsList = new ArrayList<>();
		for (FutureKey<StreamIndex> future : list)
		{
			StreamIndex index = future.future.get();
			FutureRead<StreamRecords> records = checker.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data));
			recordsList.add(new FutureKey<StreamRecords>(future.publicKey, records));
		}
		return recordsList;
	}

	private static void _populateAllElementsFromUserRoot(LoadChecker checker, JsonObject rootData, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, StreamRecords records) throws IpfsConnectionException
	{
		// We want to distinguish between records which are cached for this user and which ones aren't.
		// (in theory, multiple users could have an identical element only cached in some of them which could be
		//  displayed for all of them - we will currently ignore that case and only add the last entry).
		List<String> rawCids = records.getRecord();
		List<FutureRead<StreamRecord>> loads = new ArrayList<>();
		for (String rawCid : rawCids)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(rawCid);
			FutureRead<StreamRecord> future = checker.loadCached(cid, (byte[] data) -> GlobalData.deserializeRecord(data));
			loads.add(future);
		}
		Iterator<String> cidIterator = rawCids.iterator();
		for (FutureRead<StreamRecord> future : loads)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(cidIterator.next());
			JsonObject thisElt = _loadSinglePostStruct(checker, elementsCachedForUser, future, cid);
			rootData.set(cid.toSafeString(), thisElt);
		}
	}

	private static JsonObject _loadSinglePostStruct(LoadChecker checker, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, FutureRead<StreamRecord> future, IpfsFile cid) throws IpfsConnectionException
	{
		StreamRecord record = future.get();
		
		boolean isLocalUser = (null == elementsCachedForUser);
		boolean isCachedFollowee = !isLocalUser && elementsCachedForUser.containsKey(cid);
		// In either of these cases, we will have the data cached.
		boolean isCached = (isLocalUser || isCachedFollowee);
		
		IpfsFile thumbnailCid = null;
		IpfsFile videoCid = null;
		if (isLocalUser)
		{
			// In the local case, we want to look at the rest of the record and figure out what makes most sense since all entries will be pinned.
			List<DataElement> elements = record.getElements().getElement();
			thumbnailCid = _findThumbnail(elements);
			videoCid = _findLargestVideo(elements);
		}
		else if (isCachedFollowee)
		{
			// In this case, we want to just see what we recorded in the followee cache since that is what we pinned.
			FollowingCacheElement cachedElement = elementsCachedForUser.get(cid);
			thumbnailCid = cachedElement.imageHash();
			videoCid = cachedElement.leafHash();
		}
		
		String thumbnailUrl = null;
		String videoUrl = null;
		if (isCached)
		{
			// However we found these, they are expected to be in the cache and they should be locally pinned.
			// Note that we can have at most one thumbnail and one video but both are optional and there could be an entry with neither.
			if (null != thumbnailCid)
			{
				Assert.assertTrue(checker.isCached(thumbnailCid));
				thumbnailUrl = checker.getCachedUrl(thumbnailCid).toString();
			}
			if (null != videoCid)
			{
				Assert.assertTrue(checker.isCached(videoCid));
				videoUrl = checker.getCachedUrl(videoCid).toString();
			}
		}
		return _formatAsPostStruct(record.getName(), record.getDescription(), record.getPublishedSecondsUtc(), record.getDiscussion(), isCached, thumbnailUrl, videoUrl);
	}

	private static void _populatePostsForUser(LoadChecker checker, JsonObject rootData, IpfsKey publicKey, StreamRecords records)
	{
		JsonArray array = new JsonArray();
		for (String rawCid : records.getRecord())
		{
			array.add(rawCid);
		}
		rootData.set(publicKey.toPublicKey(), array);
	}

	private static List<FutureKey<StreamRecommendations>> _loadRecommendations(LoadChecker checker, List<FutureKey<StreamIndex>> list) throws IpfsConnectionException
	{
		List<FutureKey<StreamRecommendations>> recommendationsList = new ArrayList<>();
		for (FutureKey<StreamIndex> future : list)
		{
			StreamIndex index = future.future.get();
			FutureRead<StreamRecommendations> recommendations = checker.loadCached(IpfsFile.fromIpfsCid(index.getRecommendations()), (byte[] data) -> GlobalData.deserializeRecommendations(data));
			recommendationsList.add(new FutureKey<StreamRecommendations>(future.publicKey, recommendations));
		}
		return recommendationsList;
	}

	private static void _populateRecommendationsForUser(LoadChecker checker, JsonObject rootData, IpfsKey publicKey,StreamRecommendations recommendations)
	{
		JsonArray array = new JsonArray();
		for (String rawCid : recommendations.getUser())
		{
			array.add(rawCid);
		}
		rootData.set(publicKey.toPublicKey(), array);
	}

	private static IpfsFile _findThumbnail(List<DataElement> elements)
	{
		IpfsFile thumbnailCid = null;
		for (DataElement leaf : elements)
		{
			IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
			if (ElementSpecialType.IMAGE == leaf.getSpecial())
			{
				thumbnailCid = leafCid;
				break;
			}
		}
		return thumbnailCid;
	}

	private static IpfsFile _findLargestVideo(List<DataElement> elements)
	{
		IpfsFile videoCid = null;
		int largestEdge = 0;
		for (DataElement leaf : elements)
		{
			IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
			if (leaf.getMime().startsWith("video/"))
			{
				int maxEdge = Math.max(leaf.getHeight(), leaf.getWidth());
				if (maxEdge > largestEdge)
				{
					videoCid = leafCid;
				}
			}
		}
		return videoCid;
	}

	private static List<FutureKey<StreamIndex>> _loadStreamIndices(LoadChecker checker, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, FollowIndex followIndex)
	{
		List<FutureKey<StreamIndex>> indices = new ArrayList<>();
		_startLoad(indices, checker, ourPublicKey, lastPublishedIndex);
		for(FollowRecord record : followIndex)
		{
			_startLoad(indices, checker, record.publicKey(), record.lastFetchedRoot());
		}
		return indices;
	}

	private static JsonObject _dataUserInfo(LoadChecker checker, List<FutureKey<StreamIndex>> indices) throws IpfsConnectionException
	{
		JsonObject dataUserInfo = new JsonObject();
		List<FutureKey<StreamDescription>> descriptions = _loadDescriptions(checker, indices);
		for (FutureKey<StreamDescription> future : descriptions)
		{
			JsonObject json = _populateJsonForDescription(checker, future.future.get());
			dataUserInfo.set(future.publicKey.toPublicKey(), json);
		}
		return dataUserInfo;
	}

	private static JsonArray _dataFollowing(FollowIndex followIndex)
	{
		JsonArray dataFollowing = new JsonArray();
		for(FollowRecord record : followIndex)
		{
			dataFollowing.add(record.publicKey().toPublicKey());
		}
		return dataFollowing;
	}

	private static IpfsFile _getLastKnownIndexForKey(IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, FollowIndex followIndex, IpfsKey userToResolve)
	{
		IpfsFile indexToLoad = null;
		if (userToResolve.equals(ourPublicKey))
		{
			indexToLoad = lastPublishedIndex;
		}
		else
		{
			for(FollowRecord record : followIndex)
			{
				if (userToResolve.equals(record.publicKey()))
				{
					indexToLoad = record.lastFetchedRoot();
					break;
				}
			}
		}
		return indexToLoad;
	}

	private static JsonObject _formatAsPostStruct(String name, String description, long publishedSecondsUtc, String discussionUrl, boolean isCached, String thumbnailUrl, String videoUrl)
	{
		JsonObject thisElt = new JsonObject();
		thisElt.set("name", name);
		thisElt.set("description", description);
		thisElt.set("publishedSecondsUtc", publishedSecondsUtc);
		thisElt.set("discussionUrl", discussionUrl);
		thisElt.set("cached", isCached);
		if (isCached)
		{
			thisElt.set("thumbnailUrl", thumbnailUrl);
			thisElt.set("videoUrl", videoUrl);
		}
		return thisElt;
	}


	private static record FutureKey<T>(IpfsKey publicKey, FutureRead<T> future)
	{
	}
}
