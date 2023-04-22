package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.FutureSizedRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.SizeLimits;

import io.ipfs.cid.Cid;


public class TestFolloweeRefreshLogic
{
	private static final IpfsKey DUMMY_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testNewEmptyFollow() throws Throwable
	{
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
		Assert.assertEquals("name", testSupport.lastName);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewElementsPinned().length);
	}

	@Test
	public void testNewSingleFollowNoLeaf() throws Throwable
	{
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		index = _addElementToStream(data, index, _storeRecord(data, "Name", null, null));
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		// We don't add leaf-less entries to the followee index, since that would be redundant.
		TestSupport testSupport = new TestSupport(data, originalElements);
		IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
		Assert.assertEquals("name", testSupport.lastName);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(1, testSupport.getAndClearNewElementsPinned().length);
	}

	@Test
	public void testNewSingleFollowSmallLeaf() throws Throwable
	{
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		index = _addElementToStream(data, index, _storeRecord(data, "Name", new byte[] {1}, new byte[] {1, 2}));
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
		Assert.assertEquals("name", testSupport.lastName);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(3, result[0].combinedSizeBytes());
		Assert.assertEquals(1, testSupport.getAndClearNewElementsPinned().length);
	}

	@Test
	public void testStopFollow() throws Throwable
	{
		// First, start following.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		index = _addElementToStream(data, index, _storeRecord(data, "Name", new byte[] {1}, null));
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
		Assert.assertEquals("name", testSupport.lastName);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(1, result[0].combinedSizeBytes());
		Assert.assertEquals(1, testSupport.getAndClearNewElementsPinned().length);
		
		// Now, stop following.
		originalElements = result;
		oldIndexElement = index;
		newIndexElement = null;
		currentCacheUsageInBytes = 0L;
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewElementsPinned().length);
	}

	@Test
	public void testRefreshNoLeaves() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
		Assert.assertEquals("name", testSupport.lastName);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewElementsPinned().length);
		
		// Now, add an element with no leaves and refresh.
		IpfsFile oldIndex = index;
		index = _addElementToStream(data, index, _storeRecord(data, "Name", null, null));
		originalElements = result;
		oldIndexElement = oldIndex;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		// We don't add leaf-less entries to the followee index, since that would be redundant.
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(1, testSupport.getAndClearNewElementsPinned().length);
	}

	@Test
	public void testRefreshSmallLeaves() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
		Assert.assertEquals("name", testSupport.lastName);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewElementsPinned().length);
		
		// Now, add an element with small leaves and refresh.
		IpfsFile oldIndex = index;
		index = _addElementToStream(data, index, _storeRecord(data, "Name", new byte[] {1}, new byte[] {1, 2}));
		originalElements = result;
		oldIndexElement = oldIndex;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(3, result[0].combinedSizeBytes());
		Assert.assertEquals(1, testSupport.getAndClearNewElementsPinned().length);
	}

	@Test
	public void testRefreshHugeLeaves() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
		Assert.assertEquals("name", testSupport.lastName);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewElementsPinned().length);
		
		// Now, add an element with big leaves and refresh.
		IpfsFile oldIndex = index;
		index = _addElementToStream(data, index, _storeRecord(data, "Name", new byte[] {1, 2, 3, 4, 5, 6}, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}));
		originalElements = result;
		oldIndexElement = oldIndex;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(1, result.length);
		// NOTE:  Since there is only one element, it is currently allowed to overflow the cache.
		// TODO:  Change this when we apply this limit.
		Assert.assertEquals(16, result[0].combinedSizeBytes());
		Assert.assertEquals(1, testSupport.getAndClearNewElementsPinned().length);
	}

	@Test
	public void testBrokenMetaData() throws Throwable
	{
		// Create a normal user with a single record.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		index = _addElementToStream(data, index, _storeRecord(data, "Name", null, null));
		// Now, break the recommendations (just misc meta-data).
		StreamIndex indexObject = GlobalData.deserializeIndex(data.get(index));
		data.remove(IpfsFile.fromIpfsCid(indexObject.getRecommendations()));
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		
		// We expect a failure if we can't pin a meta-data element for network reasons.
		boolean didFail = false;
		try
		{
			TestSupport testSupport = new TestSupport(data, originalElements);
			IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
			FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
	}

	@Test
	public void testBrokenRecord() throws Throwable
	{
		// Create a normal user with a single record.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		index = _addElementToStream(data, index, _storeRecord(data, "Name", null, null));
		// Now, break the record by dropping it form storage.
		IpfsFile[] records = _readRecordHashes(data, index);
		Assert.assertEquals(1, records.length);
		data.remove(records[0]);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		
		// We expect a failure if we can't pin a meta-data element for network reasons.
		boolean didFail = false;
		try
		{
			TestSupport testSupport = new TestSupport(data, originalElements);
			IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
			// If the start was a success, we should see the name.
			Assert.assertEquals("name", testSupport.lastName);
			FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
	}

	@Test
	public void testBrokenLeaf() throws Throwable
	{
		// Create a normal user with a single record with leaves.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		index = _addElementToStream(data, index, _storeRecord(data, "Name", new byte[] {1}, new byte[] { 1, 2, 3 }));
		
		// Now, break the leaf by dropping it form storage.
		IpfsFile[] records = _readRecordHashes(data, index);
		Assert.assertEquals(1, records.length);
		StreamRecord record = GlobalData.deserializeRecord(data.get(records[0]));
		List<DataElement> leaves = record.getElements().getElement();
		Assert.assertEquals(2, leaves.size());
		IpfsFile leafCid = IpfsFile.fromIpfsCid(leaves.get(0).getCid());
		data.remove(leafCid);
		
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		
		// We expect that this will succeed, since it isn't a meta-data failure, but we will decide NOT to cache this element.
		TestSupport testSupport = new TestSupport(data, originalElements);
		IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
		Assert.assertEquals("name", testSupport.lastName);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(1, testSupport.getAndClearNewElementsPinned().length);
	}

	@Test
	public void testRemoveRecord() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 100L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
		Assert.assertEquals("name", testSupport.lastName);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewElementsPinned().length);
		
		// Add 2 elements and refresh.
		IpfsFile oldIndex = index;
		IpfsFile record1 = _storeRecord(data, "Name1", new byte[] {1}, new byte[] {1, 1});
		index = _addElementToStream(data, index, record1);
		index = _addElementToStream(data, index, _storeRecord(data, "Name2", new byte[] {2}, new byte[] {1, 1, 2}));
		originalElements = result;
		oldIndexElement = oldIndex;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(2, result.length);
		Assert.assertEquals(3, result[0].combinedSizeBytes());
		Assert.assertEquals(4, result[1].combinedSizeBytes());
		Assert.assertEquals(2, testSupport.getAndClearNewElementsPinned().length);
		
		// Remove the first element and add a new one, then refresh.
		oldIndex = index;
		index = _removeElementFromStream(data, index, record1);
		index = _addElementToStream(data, index, _storeRecord(data, "Name3", new byte[] {3}, new byte[] {1, 1, 1, 3}));
		originalElements = result;
		oldIndexElement = oldIndex;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(2, result.length);
		Assert.assertEquals(4, result[0].combinedSizeBytes());
		Assert.assertEquals(5, result[1].combinedSizeBytes());
		Assert.assertEquals(1, testSupport.getAndClearNewElementsPinned().length);
		
		// Verify that the removed elements have been deleted from the local data (will still be in upstream).
		_verifyRecordDeleted(testSupport, data, record1);
	}

	@Test
	public void testSizeLimit_StreamIndex() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		byte[] raw = new byte[(int)SizeLimits.MAX_INDEX_SIZE_BYTES + 1];
		IpfsFile rawHash = _fakeHash(raw);
		data.put(rawHash, raw);
		
		_commonSizeCheck(data, rawHash);
	}

	@Test
	public void testSizeLimit_StreamRecommendations() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		byte[] raw = new byte[(int)SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES + 1];
		IpfsFile rawHash = _fakeHash(raw);
		data.put(rawHash, raw);
		
		byte[] userPic = new byte[] {'a','b','c'};
		IpfsFile userPicFile = _fakeHash(userPic);
		data.put(userPicFile, userPic);
		
		IpfsFile descriptionHash = _store_StreamDescription(data, userPicFile);
		
		IpfsFile recordsHash = _store_StreamRecords(data);
		
		IpfsFile indexHash = _store_StreamIndex(data, rawHash, descriptionHash, recordsHash);
		
		_commonSizeCheck(data, indexHash);
	}

	@Test
	public void testSizeLimit_StreamDescription() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		byte[] raw = new byte[(int)SizeLimits.MAX_DESCRIPTION_SIZE_BYTES + 1];
		IpfsFile rawHash = _fakeHash(raw);
		data.put(rawHash, raw);
		
		IpfsFile recommendationsHash = _store_StreamRecommendations(data);
		
		IpfsFile recordsHash = _store_StreamRecords(data);
		
		IpfsFile indexHash = _store_StreamIndex(data, recommendationsHash, rawHash, recordsHash);
		
		_commonSizeCheck(data, indexHash);
	}

	@Test
	public void testSizeLimit_StreamRecords() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		byte[] raw = new byte[(int)SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES + 1];
		IpfsFile rawHash = _fakeHash(raw);
		data.put(rawHash, raw);
		
		IpfsFile recommendationsHash = _store_StreamRecommendations(data);
		
		byte[] userPic = new byte[] {'a','b','c'};
		IpfsFile userPicFile = _fakeHash(userPic);
		data.put(userPicFile, userPic);
		
		IpfsFile descriptionHash = _store_StreamDescription(data, userPicFile);
		
		IpfsFile indexHash = _store_StreamIndex(data, recommendationsHash, descriptionHash, rawHash);
		
		_commonSizeCheck(data, indexHash);
	}

	@Test
	public void testSizeLimit_descriptionSize() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		byte[] raw = new byte[(int)SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES + 1];
		IpfsFile rawHash = _fakeHash(raw);
		data.put(rawHash, raw);
		
		IpfsFile recommendationsHash = _store_StreamRecommendations(data);
		
		IpfsFile descriptionHash = _store_StreamDescription(data, rawHash);
		
		IpfsFile recordsHash = _store_StreamRecords(data);
		
		IpfsFile indexHash = _store_StreamIndex(data, recommendationsHash, descriptionHash, recordsHash);
		
		_commonSizeCheck(data, indexHash);
	}

	@Test
	public void testBigElementAddRemove() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
		Assert.assertEquals("name", testSupport.lastName);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewElementsPinned().length);
		
		// Now, add an illegally-sized element and verify that we fail the refresh.
		IpfsFile oldIndex = index;
		byte[] raw = new byte[(int)SizeLimits.MAX_RECORD_SIZE_BYTES + 1];
		IpfsFile rawHash = _fakeHash(raw);
		data.put(rawHash, raw);
		index = _addElementToStream(data, index, rawHash);
		originalElements = result;
		oldIndexElement = oldIndex;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		// We don't add leaf-less entries to the followee index, since that would be redundant.
		boolean didAdd = false;
		try
		{
			FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
			didAdd = true;
		}
		catch (SizeConstraintException e)
		{
			didAdd = false;
		}
		Assert.assertFalse(didAdd);
		result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewElementsPinned().length);
		data.remove(rawHash);
		
		// Now, replace it with a valid element (with no leaves) and see that we do see this pin but also the unpin of the previous (vacuous unpin).
		oldIndex = index;
		index = _removeElementFromStream(data, index, rawHash);
		index = _addElementToStream(data, index, _storeRecord(data, "Name", null, null));
		originalElements = result;
		oldIndexElement = oldIndex;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		// We will end up trying to remove something we never pinned, due to the size, so set that in the support.
		testSupport.nonPinnedElement = rawHash;
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(1, testSupport.getAndClearNewElementsPinned().length);
		// Make sure we observed this non-pin action.
		Assert.assertNull(testSupport.nonPinnedElement);
	}


	private void _commonSizeCheck(Map<IpfsFile, byte[]> data, IpfsFile indexHash) throws IpfsConnectionException, FailedDeserializationException
	{
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followCacheTargetBytes = 100L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile newIndexElement = indexHash;
		long currentCacheUsageInBytes = 0L;
		boolean didFail = false;
		try
		{
			TestSupport testSupport = new TestSupport(data, originalElements);
			IpfsFile oldIndexElement = FolloweeRefreshLogic.startFollowing(testSupport, newIndexElement);
			// If the start was a success, we should see the name.
			Assert.assertEquals("name", testSupport.lastName);
			FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		}
		catch (SizeConstraintException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
	}

	private static IpfsFile _buildEmptyUser(Map<IpfsFile, byte[]> data) throws SizeConstraintException
	{
		// We build a fake user.
		byte[] userPic = new byte[] {'a','b','c'};
		IpfsFile userPicFile = _fakeHash(userPic);
		data.put(userPicFile, userPic);
		
		IpfsFile recommendationsHash = _store_StreamRecommendations(data);
		
		IpfsFile descriptionHash = _store_StreamDescription(data, userPicFile);
		
		IpfsFile recordsHash = _store_StreamRecords(data);
		
		IpfsFile indexHash = _store_StreamIndex(data, recommendationsHash, descriptionHash, recordsHash);
		return indexHash;
	}

	private static IpfsFile _store_StreamRecommendations(Map<IpfsFile, byte[]> data) throws SizeConstraintException
	{
		StreamRecommendations recommendations = new StreamRecommendations();
		byte[] serialized = GlobalData.serializeRecommendations(recommendations);
		IpfsFile recommendationsHash = _fakeHash(serialized);
		data.put(recommendationsHash, serialized);
		return recommendationsHash;
	}

	private static IpfsFile _store_StreamDescription(Map<IpfsFile, byte[]> data, IpfsFile userPicFile) throws SizeConstraintException
	{
		byte[] serialized;
		StreamDescription description = new StreamDescription();
		description.setName("name");
		description.setDescription("description");
		description.setPicture(userPicFile.toSafeString());
		serialized = GlobalData.serializeDescription(description);
		IpfsFile descriptionHash = _fakeHash(serialized);
		data.put(descriptionHash, serialized);
		return descriptionHash;
	}

	private static IpfsFile _store_StreamRecords(Map<IpfsFile, byte[]> data) throws SizeConstraintException
	{
		byte[] serialized;
		StreamRecords records = new StreamRecords();
		serialized = GlobalData.serializeRecords(records);
		IpfsFile recordsHash = _fakeHash(serialized);
		data.put(recordsHash, serialized);
		return recordsHash;
	}

	private static IpfsFile _store_StreamIndex(Map<IpfsFile, byte[]> data, IpfsFile recommendationsHash, IpfsFile descriptionHash, IpfsFile recordsHash) throws SizeConstraintException
	{
		byte[] serialized;
		StreamIndex index = new StreamIndex();
		index.setVersion(1);
		index.setDescription(descriptionHash.toSafeString());
		index.setRecommendations(recommendationsHash.toSafeString());
		index.setRecords(recordsHash.toSafeString());
		serialized = GlobalData.serializeIndex(index);
		IpfsFile indexHash = _fakeHash(serialized);
		data.put(indexHash, serialized);
		return indexHash;
	}

	private static IpfsFile _storeRecord(Map<IpfsFile, byte[]> data, String name, byte[] thumbnail, byte[] video) throws SizeConstraintException
	{
		// Create the record.
		StreamRecord record = new StreamRecord();
		record.setName(name);
		record.setDescription("nothing");
		record.setPublishedSecondsUtc(1L);
		record.setPublisherKey(DUMMY_KEY.toPublicKey());
		DataArray array = new DataArray();
		if (null != thumbnail)
		{
			IpfsFile thumbnailHash = _fakeHash(thumbnail);
			data.put(thumbnailHash, thumbnail);
			DataElement element = new DataElement();
			element.setMime("image/jpeg");
			element.setSpecial(ElementSpecialType.IMAGE);
			element.setCid(thumbnailHash.toSafeString());
			array.getElement().add(element);
		}
		if (null != video)
		{
			IpfsFile videoHash = _fakeHash(video);
			data.put(videoHash, video);
			DataElement element = new DataElement();
			element.setMime("video/webm");
			element.setHeight(720);
			element.setWidth(1280);
			element.setCid(videoHash.toSafeString());
			array.getElement().add(element);
		}
		record.setElements(array);
		byte[] serialized = GlobalData.serializeRecord(record);
		IpfsFile recordHash = _fakeHash(serialized);
		Assert.assertNull(data.put(recordHash, serialized));
		return recordHash;
	}

	private static IpfsFile _addElementToStream(Map<IpfsFile, byte[]> data, IpfsFile indexHash, IpfsFile recordHash) throws FailedDeserializationException, SizeConstraintException
	{
		// Read the existing stream.
		StreamIndex index = GlobalData.deserializeIndex(data.get(indexHash));
		IpfsFile recordsHash = IpfsFile.fromIpfsCid(index.getRecords());
		StreamRecords records = GlobalData.deserializeRecords(data.get(recordsHash));
		records.getRecord().add(recordHash.toSafeString());
		byte[] serialized = GlobalData.serializeRecords(records);
		Assert.assertNotNull(data.remove(recordsHash));
		recordsHash = _fakeHash(serialized);
		Assert.assertNull(data.put(recordsHash, serialized));
		
		index.setRecords(recordsHash.toSafeString());
		serialized = GlobalData.serializeIndex(index);
		Assert.assertNotNull(data.remove(indexHash));
		indexHash = _fakeHash(serialized);
		Assert.assertNull(data.put(indexHash, serialized));
		return indexHash;
	}

	private static IpfsFile _removeElementFromStream(Map<IpfsFile, byte[]> data, IpfsFile indexHash, IpfsFile recordHash) throws FailedDeserializationException, SizeConstraintException
	{
		// Read the existing stream.
		StreamIndex index = GlobalData.deserializeIndex(data.get(indexHash));
		IpfsFile recordsHash = IpfsFile.fromIpfsCid(index.getRecords());
		StreamRecords records = GlobalData.deserializeRecords(data.get(recordsHash));
		List<String> recordList = records.getRecord();
		Assert.assertTrue(recordList.remove(recordHash.toSafeString()));
		byte[] serialized = GlobalData.serializeRecords(records);
		Assert.assertNotNull(data.remove(recordsHash));
		recordsHash = _fakeHash(serialized);
		Assert.assertNull(data.put(recordsHash, serialized));
		
		index.setRecords(recordsHash.toSafeString());
		serialized = GlobalData.serializeIndex(index);
		Assert.assertNotNull(data.remove(indexHash));
		indexHash = _fakeHash(serialized);
		Assert.assertNull(data.put(indexHash, serialized));
		return indexHash;
	}

	private static void _verifyRecordDeleted(TestSupport testSupport, Map<IpfsFile, byte[]> data, IpfsFile recordHash) throws FailedDeserializationException
	{
		// Verify the record isn't in testSupport.
		testSupport.verifyNotPresentOrPinned(recordHash);
		
		// Read the record from our data (it is always there, only removed from testSupport).
		StreamRecord record = GlobalData.deserializeRecord(data.get(recordHash));
		
		// Read the leaf elements and make sure that they aren't cached.
		for (DataElement element : record.getElements().getElement())
		{
			testSupport.verifyNotPresentOrPinned(IpfsFile.fromIpfsCid(element.getCid()));
		}
	}

	private IpfsFile[] _readRecordHashes(Map<IpfsFile, byte[]> data, IpfsFile indexHash) throws FailedDeserializationException
	{
		StreamIndex index = GlobalData.deserializeIndex(data.get(indexHash));
		IpfsFile recordsHash = IpfsFile.fromIpfsCid(index.getRecords());
		StreamRecords records = GlobalData.deserializeRecords(data.get(recordsHash));
		return records.getRecord().stream().map((String rawCid) -> IpfsFile.fromIpfsCid(rawCid)).collect(Collectors.toList()).toArray((int size) -> new IpfsFile[size]);
	}

	private static IpfsFile _fakeHash(byte[] data)
	{
		String prefix = "12204deaa860e7feea3df33b6fd9426c705bcaeaac05eaeb4a69c4421304";
		int hashcode = Arrays.hashCode(data);
		long unsigned = Integer.toUnsignedLong(hashcode);
		long hex = ((0xFF & (unsigned >> 24)) << 24) | ((0xFF & (unsigned >> 16)) << 16) | ((0xFF & (unsigned >> 8)) << 8) | (0xFF & unsigned);
		String hexString = Long.toHexString(hex);
		while (hexString.length() < 8)
		{
			hexString = "0" + hexString;
		}
		return IpfsFile.fromIpfsCid(Cid.fromHex(prefix + hexString).toBase58());
	}


	private static class TestSupport implements FolloweeRefreshLogic.IRefreshSupport, FolloweeRefreshLogic.IStartSupport
	{
		private final Map<IpfsFile, byte[]> _upstreamData;
		private final List<FollowingCacheElement> _list;
		private final Map<IpfsFile, byte[]> _data = new HashMap<>();
		private final List<IpfsFile> _deferredMetaUnpin = new ArrayList<>();
		private final List<IpfsFile> _deferredFileUnpin = new ArrayList<>();
		private final Map<IpfsFile, Integer> _metaDataPinCount = new HashMap<>();
		private final Map<IpfsFile, Integer> _filePinCount = new HashMap<>();
		private final List<IpfsFile> _newElementsPinned = new ArrayList<>();
		
		// Miscellaneous other checks.
		public IpfsFile nonPinnedElement;
		public String lastName;
		
		public TestSupport(Map<IpfsFile, byte[]> upstreamData, FollowingCacheElement[] initial)
		{
			_upstreamData = upstreamData;
			_list = new ArrayList<>(List.of(initial));
		}
		public void verifyNotPresentOrPinned(IpfsFile hash)
		{
			Assert.assertFalse(_data.containsKey(hash));
			Assert.assertFalse(_metaDataPinCount.containsKey(hash));
			Assert.assertFalse(_filePinCount.containsKey(hash));
		}
		public FollowingCacheElement[] getList()
		{
			// Process defers.
			for (IpfsFile cid : _deferredMetaUnpin)
			{
				Assert.assertTrue(_data.containsKey(cid));
				Assert.assertTrue(_metaDataPinCount.containsKey(cid));
				Assert.assertTrue(!_filePinCount.containsKey(cid));
				int count = _metaDataPinCount.get(cid);
				count -= 1;
				if (count > 0)
				{
					_metaDataPinCount.put(cid, count);
				}
				else
				{
					_metaDataPinCount.remove(cid);
					_data.remove(cid);
				}
			}
			_deferredMetaUnpin.clear();
			for (IpfsFile cid : _deferredFileUnpin)
			{
				Assert.assertTrue(_data.containsKey(cid));
				Assert.assertTrue(!_metaDataPinCount.containsKey(cid));
				Assert.assertTrue(_filePinCount.containsKey(cid));
				int count = _filePinCount.get(cid);
				count -= 1;
				if (count > 0)
				{
					_filePinCount.put(cid, count);
				}
				else
				{
					_filePinCount.remove(cid);
					_data.remove(cid);
				}
			}
			_deferredFileUnpin.clear();
			
			// Return list.
			return _list.toArray((int size) -> new FollowingCacheElement[size]);
		}
		public IpfsFile[] getAndClearNewElementsPinned()
		{
			IpfsFile[] array = _newElementsPinned.toArray((int size) -> new IpfsFile[size]);
			_newElementsPinned.clear();
			return array;
		}
		@Override
		public void logMessage(String message)
		{
			// No logging in tests.
		}
		@Override
		public void followeeDescriptionNewOrUpdated(String name, String description, IpfsFile userPicCid, String emailOrNull, String websiteOrNull)
		{
			this.lastName = name;
		}
		@Override
		public void newElementPinned(IpfsFile elementHash, String name, String description, long publishedSecondsUtc, String discussionUrl, String publisherKey, int leafReferenceCount)
		{
			_newElementsPinned.add(elementHash);
		}
		@Override
		public FutureSize getSizeInBytes(IpfsFile cid)
		{
			FutureSize future = new FutureSize();
			if (_data.containsKey(cid))
			{
				future.success(_data.get(cid).length);
			}
			else if (_upstreamData.containsKey(cid))
			{
				future.success(_upstreamData.get(cid).length);
			}
			else
			{
				future.failure(new IpfsConnectionException("size", cid, null));
			}
			return future;
		}
		@Override
		public FuturePin addMetaDataToFollowCache(IpfsFile cid)
		{
			FuturePin future = new FuturePin(cid);
			if (_data.containsKey(cid))
			{
				Assert.assertTrue(_metaDataPinCount.containsKey(cid));
				int count = _metaDataPinCount.get(cid);
				count += 1;
				_metaDataPinCount.put(cid, count);
				Assert.assertTrue(!_filePinCount.containsKey(cid));
				future.success();
			}
			else if (_upstreamData.containsKey(cid))
			{
				Assert.assertTrue(!_metaDataPinCount.containsKey(cid));
				Assert.assertTrue(!_filePinCount.containsKey(cid));
				_data.put(cid, _upstreamData.get(cid));
				_metaDataPinCount.put(cid, 1);
				future.success();
			}
			else
			{
				future.failure(new IpfsConnectionException("pin", cid, null));
			}
			return future;
		}
		@Override
		public void deferredRemoveMetaDataFromFollowCache(IpfsFile cid)
		{
			// Note that we will not add this if we know it is one we are missing.
			if (!cid.equals(this.nonPinnedElement))
			{
				_deferredMetaUnpin.add(cid);
			}
		}
		@Override
		public FuturePin addFileToFollowCache(IpfsFile cid)
		{
			FuturePin future = new FuturePin(cid);
			if (_data.containsKey(cid))
			{
				Assert.assertTrue(_filePinCount.containsKey(cid));
				int count = _filePinCount.get(cid);
				count += 1;
				_filePinCount.put(cid, count);
				Assert.assertTrue(!_metaDataPinCount.containsKey(cid));
				future.success();
			}
			else if (_upstreamData.containsKey(cid))
			{
				Assert.assertTrue(!_filePinCount.containsKey(cid));
				Assert.assertTrue(!_metaDataPinCount.containsKey(cid));
				_data.put(cid, _upstreamData.get(cid));
				_filePinCount.put(cid, 1);
				future.success();
			}
			else
			{
				future.failure(new IpfsConnectionException("pin", cid, null));
			}
			return future;
		}
		@Override
		public void deferredRemoveFileFromFollowCache(IpfsFile cid)
		{
			_deferredFileUnpin.add(cid);
		}
		@Override
		public <R> FutureRead<R> loadCached(IpfsFile file, DataDeserializer<R> decoder)
		{
			Assert.assertTrue(_data.containsKey(file));
			FutureRead<R> future = new FutureRead<R>();
			try
			{
				future.success(decoder.apply(_data.get(file)));
			}
			catch (FailedDeserializationException e)
			{
				future.failureInDecoding(e);
			}
			return future;
		}
		@Override
		public IpfsFile getImageForCachedElement(IpfsFile elementHash)
		{
			FollowingCacheElement match = null;
			for (FollowingCacheElement elt : _list)
			{
				if (elt.elementHash().equals(elementHash))
				{
					Assert.assertNull(match);
					match = elt;
				}
			}
			return (null != match)
					? match.imageHash()
					: null
			;
		}
		@Override
		public IpfsFile getLeafForCachedElement(IpfsFile elementHash)
		{
			FollowingCacheElement match = null;
			for (FollowingCacheElement elt : _list)
			{
				if (elt.elementHash().equals(elementHash))
				{
					Assert.assertNull(match);
					match = elt;
				}
			}
			return (null != match)
					? match.leafHash()
					: null
			;
		}
		@Override
		public void addElementToCache(IpfsFile elementHash, IpfsFile imageHash, IpfsFile audioLeaf, IpfsFile videoLeaf, int videoEdgeSize, long combinedSizeBytes)
		{
			IpfsFile leafHash = (null != audioLeaf) ? audioLeaf : videoLeaf;
			_list.add(new FollowingCacheElement(elementHash, imageHash, leafHash, combinedSizeBytes));
		}
		@Override
		public void removeElementFromCache(IpfsFile elementHash)
		{
			int match = -1;
			for (int i = 0; i < _list.size(); ++i)
			{
				if (_list.get(i).elementHash().equals(elementHash))
				{
					Assert.assertEquals(-1, match);
					match = i;
				}
			}
			if (match >= 0)
			{
				_list.remove(match);
			}
			else
			{
				// If we didn't find it, make sure this is one we know we didn't pin.
				Assert.assertEquals(this.nonPinnedElement, elementHash);
				this.nonPinnedElement = null;
			}
		}
		@Override
		public <R> FutureSizedRead<R> loadNotCached(IpfsFile file, String context, long maxSizeInBytes, DataDeserializer<R> decoder)
		{
			Assert.assertTrue(_upstreamData.containsKey(file));
			// While we could technically see something pinned in the non-cached load, we don't expect that in this test.
			Assert.assertFalse(_data.containsKey(file));
			FutureSizedRead<R> future = new FutureSizedRead<R>();
			try
			{
				byte[] data = _upstreamData.get(file);
				if (data.length <= maxSizeInBytes)
				{
					future.success(decoder.apply(data));
				}
				else
				{
					// One of the tests does use this.
					future.failureInSizeCheck(new SizeConstraintException(context, data.length, maxSizeInBytes));
				}
			}
			catch (FailedDeserializationException e)
			{
				future.failureInDecoding(e);
			}
			return future;
		}
		@Override
		public IpfsFile uploadNewData(byte[] data) throws IpfsConnectionException
		{
			IpfsFile hash = _fakeHash(data);
			Assert.assertFalse(_data.containsKey(hash));
			Assert.assertFalse(_metaDataPinCount.containsKey(hash));
			_data.put(hash, data);
			// This case only uploads meta-data.
			_metaDataPinCount.put(hash, 1);
			return hash;
		}
	}
}
