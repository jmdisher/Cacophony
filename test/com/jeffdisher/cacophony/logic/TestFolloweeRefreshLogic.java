package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.DataDeserializer;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.SizeLimits;
import com.jeffdisher.cacophony.utils.SizeLimits2;


public class TestFolloweeRefreshLogic
{
	@Test
	public void testNewEmptyFollow() throws Throwable
	{
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followeeCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		Assert.assertEquals("name", testSupport.lastName);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
	}

	@Test
	public void testNewSingleFollowNoLeaf() throws Throwable
	{
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		IpfsFile element = _storeRecord(data, "Name", null, null);
		index = _addElementToStream(data, index, element);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followeeCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		// First sync doesn't fetch records so run again.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		Assert.assertEquals("name", testSupport.lastName);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(element, result[0].elementHash());
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
	}

	@Test
	public void testNewSingleFollowSmallLeaf() throws Throwable
	{
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		index = _addElementToStream(data, index, _storeRecord(data, "Name", new byte[] {1}, new byte[] {1, 2}));
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followeeCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		// First sync doesn't fetch records so run again.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		Assert.assertEquals("name", testSupport.lastName);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(3, result[0].combinedSizeBytes());
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
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
		prefs.followeeCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		// First sync doesn't fetch records so run again.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		Assert.assertEquals("name", testSupport.lastName);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(1, result[0].combinedSizeBytes());
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		
		// Now, stop following.
		originalElements = result;
		oldIndexElement = index;
		newIndexElement = null;
		currentCacheUsageInBytes = 0L;
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(1, testSupport.getAndClearRecordsDisappeared().length);
	}

	@Test
	public void testRefreshNoLeaves() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followeeCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals("name", testSupport.lastName);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		
		// Now, add an element with no leaves and refresh.
		IpfsFile oldIndex = index;
		IpfsFile element = _storeRecord(data, "Name", null, null);
		index = _addElementToStream(data, index, element);
		originalElements = result;
		oldIndexElement = oldIndex;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(element, result[0].elementHash());
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
	}

	@Test
	public void testRefreshSmallLeaves() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followeeCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals("name", testSupport.lastName);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		
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
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
	}

	@Test
	public void testRefreshHugeLeaves() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followeeCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals("name", testSupport.lastName);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		
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
		Assert.assertEquals(16, result[0].combinedSizeBytes());
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
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
		prefs.followeeCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		
		// We expect a failure if we can't pin a meta-data element for network reasons.
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean didFail = false;
		try
		{
			FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
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
		prefs.followeeCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		
		// Now that we use incremental sync, this won't fail to synchronize records, it will just mark them as temporary skips.
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		// First sync doesn't fetch records so run again.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		
		// Verify that this record was temporarily skipped.
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(1, testSupport.temporarySkips.size());
		Assert.assertEquals(records[0], testSupport.temporarySkips.get(0));
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
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
		prefs.followeeCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		
		// We will cache the element but neither of the leaves, even though we would normally consider them since we will gracfully fail.
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		// First sync doesn't fetch records so run again.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		Assert.assertEquals("name", testSupport.lastName);
		FollowingCacheElement[] result = testSupport.getList();
		
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(records[0], result[0].elementHash());
		Assert.assertNull(result[0].imageHash());
		Assert.assertNull(result[0].leafHash());
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		// This should just cause us to skip the leaves, but still sync everything.
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
	}

	@Test
	public void testRemoveRecord() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followeeCacheTargetBytes = 100L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals("name", testSupport.lastName);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		
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
		Assert.assertEquals(2, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(2, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		
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
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(1, testSupport.getAndClearRecordsDisappeared().length);
		
		// Verify that the removed elements have been deleted from the local data (will still be in upstream).
		_verifyRecordDeleted(testSupport, data, record1);
	}

	@Test
	public void testSizeLimit_StreamIndex() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		byte[] raw = new byte[(int)SizeLimits.MAX_INDEX_SIZE_BYTES + 1];
		IpfsFile rawHash = MockSingleNode.generateHash(raw);
		data.put(rawHash, raw);
		
		_commonSizeCheck(data, rawHash);
	}

	@Test
	public void testSizeLimit_StreamRecommendations() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		byte[] raw = new byte[(int)SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES + 1];
		IpfsFile rawHash = MockSingleNode.generateHash(raw);
		data.put(rawHash, raw);
		
		byte[] userPic = new byte[] {'a','b','c'};
		IpfsFile userPicFile = MockSingleNode.generateHash(userPic);
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
		IpfsFile rawHash = MockSingleNode.generateHash(raw);
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
		byte[] raw = new byte[(int)SizeLimits2.MAX_RECORDS_SIZE_BYTES + 1];
		IpfsFile rawHash = MockSingleNode.generateHash(raw);
		data.put(rawHash, raw);
		
		IpfsFile recommendationsHash = _store_StreamRecommendations(data);
		
		byte[] userPic = new byte[] {'a','b','c'};
		IpfsFile userPicFile = MockSingleNode.generateHash(userPic);
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
		IpfsFile rawHash = MockSingleNode.generateHash(raw);
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
		prefs.followeeCacheTargetBytes = 5L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals("name", testSupport.lastName);
		FollowingCacheElement[] result = testSupport.getList();
		Assert.assertEquals(0, result.length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		
		// Now, add an illegally-sized element and verify that we fail the refresh.
		IpfsFile oldIndex = index;
		byte[] raw = new byte[(int)SizeLimits.MAX_RECORD_SIZE_BYTES + 1];
		IpfsFile rawHash = MockSingleNode.generateHash(raw);
		data.put(rawHash, raw);
		index = _addElementToStream(data, index, rawHash);
		originalElements = result;
		oldIndexElement = oldIndex;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		
		// This should end up marking this as a permanent failure since size/corruption problems will still be wrong on retry.
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(1, testSupport.permanentSkips.size());
		Assert.assertEquals(rawHash, testSupport.permanentSkips.get(0));
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		testSupport.clearAfterFailure();
		data.remove(rawHash);
		
		// Now, replace it with a valid element (with no leaves) and see that we do see this pin but also the unpin of the previous (vacuous unpin).
		oldIndex = index;
		index = _removeElementFromStream(data, index, rawHash);
		IpfsFile newHash = _storeRecord(data, "Name", null, null);
		index = _addElementToStream(data, index, newHash);
		originalElements = result;
		oldIndexElement = newIndexElement;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(newHash, result[0].elementHash());
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(1, testSupport.getAndClearRecordsDisappeared().length);
	}

	@Test
	public void startFollowIncremental() throws Throwable
	{
		// We want to create a user which will take 2 incremental synchronizations to begin following.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		// It synchronizes 5 at a time so add 6, some with leaves, some without.
		index = _addElementToStream(data, index, _storeRecord(data, "post0", new byte[] {0, 0}, new byte[] {1, 0}));
		index = _addElementToStream(data, index, _storeRecord(data, "post1", null, new byte[] {1, 1}));
		index = _addElementToStream(data, index, _storeRecord(data, "post2", null, null));
		index = _addElementToStream(data, index, _storeRecord(data, "post3", new byte[] {0, 3}, null));
		index = _addElementToStream(data, index, _storeRecord(data, "post4", new byte[] {0, 4}, new byte[] {1, 4}));
		index = _addElementToStream(data, index, _storeRecord(data, "post5", new byte[] {0, 5}, new byte[] {1, 5}));
		PrefsData prefs = PrefsData.defaultPrefs();
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		// First sync doesn't fetch records so run again.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		oldIndexElement = newIndexElement;
		FollowingCacheElement[] result = testSupport.getList();
		// We should see all 5 elements.
		Assert.assertEquals(FolloweeRefreshLogic.INCREMENTAL_RECORD_COUNT, result.length);
		// We should see all the records observed, but only the 5 limit pinned.
		Assert.assertEquals(6, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(FolloweeRefreshLogic.INCREMENTAL_RECORD_COUNT, testSupport.getAndClearNewRecordsPinned().length);
		
		// Refresh again to make sure that we get the remainder.
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		result = testSupport.getList();
		Assert.assertEquals(6, result.length);
		// We should see nothing new observed but the 1 new element pinned.
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
	}

	@Test
	public void startFollowBrokenRecord() throws Throwable
	{
		// We want to create a user which will take 2 incremental synchronizations to begin following.
		// We will then break the references to one of the elements as permanent, another as temporary, and make sure we see them handled that way.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		// It synchronizes 5 at a time so add 8, such that we can break a bunch in different ways, for both increments
		byte[] thumb0 = new byte[] {0, 0};
		IpfsFile post0 = _storeRecord(data, "post0", thumb0, new byte[] {1, 0});
		IpfsFile post1 = _storeRecord(data, "post1", null, new byte[] {1, 1});
		IpfsFile post2 = _storeRecord(data, "post2", null, null);
		IpfsFile post3 = _storeRecord(data, "post3", new byte[] {0, 3}, null);
		byte[] video4 = new byte[] {1, 4};
		IpfsFile post4 = _storeRecord(data, "post4", new byte[] {0, 4}, video4);
		IpfsFile post5 = _storeRecord(data, "post5", new byte[] {0, 5}, new byte[] {1, 5});
		IpfsFile post6 = _storeRecord(data, "post6", null, new byte[] {1, 6});
		IpfsFile post7 = _storeRecord(data, "post7", null, null);
		index = _addElementToStream(data, index, post0);
		index = _addElementToStream(data, index, post1);
		index = _addElementToStream(data, index, post2);
		index = _addElementToStream(data, index, post3);
		index = _addElementToStream(data, index, post4);
		index = _addElementToStream(data, index, post5);
		index = _addElementToStream(data, index, post6);
		index = _addElementToStream(data, index, post7);
		
		// Break a bunch of records and leaves.
		data.remove(MockSingleNode.generateHash(thumb0));
		data.remove(post1);
		data.put(post2, new byte[] {1,1,1});
		data.remove(MockSingleNode.generateHash(video4));
		data.remove(post5);
		data.put(post7, new byte[] {1,1,1});
		
		// Now do both parts of the sync.
		PrefsData prefs = PrefsData.defaultPrefs();
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		// First sync doesn't fetch records so run again.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		FollowingCacheElement[] result = testSupport.getList();
		// We should see only the 3, since only post6, post4, and post3 are well-formed from the initial sync.
		Assert.assertEquals(3, result.length);
		Assert.assertEquals(post3, result[0].elementHash());
		Assert.assertEquals(post4, result[1].elementHash());
		Assert.assertEquals(post6, result[2].elementHash());
		// We should see all the records observed, but only the ones we succeeded in reading pinned.
		Assert.assertEquals(8, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(3, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		// Permanent skip of post7 since it is corrupted.
		Assert.assertEquals(1, testSupport.permanentSkips.size());
		Assert.assertEquals(post7, testSupport.permanentSkips.get(0));
		// Temporary skip of post5 since it is missing.
		Assert.assertEquals(1, testSupport.temporarySkips.size());
		Assert.assertEquals(post5, testSupport.temporarySkips.get(0));
		
		// Refresh again to make sure that we get the remainder.
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(4, result.length);
		Assert.assertEquals(post3, result[0].elementHash());
		Assert.assertEquals(post4, result[1].elementHash());
		Assert.assertEquals(post6, result[2].elementHash());
		Assert.assertEquals(post0, result[3].elementHash());
		
		// We should see nothing new observed but the 1 new element pinned.
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		// Permanent skip of post2 since it is corrupted.
		Assert.assertEquals(2, testSupport.permanentSkips.size());
		Assert.assertEquals(post2, testSupport.permanentSkips.get(1));
		// Temporary skip of post1 since it is missing.
		Assert.assertEquals(2, testSupport.temporarySkips.size());
		Assert.assertEquals(post1, testSupport.temporarySkips.get(1));
	}

	@Test
	public void startFollowAddRemove() throws Throwable
	{
		// We want to create a user which will take 2 incremental synchronizations to begin following.
		// We will then add and remove some records and resync to make sure the result is as expected.
		// Add a bunch of posts, do the initial sync, then add another and remove a synced and not yet synced element.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		IpfsFile post0 = _storeRecord(data, "post0", new byte[] {0, 0}, new byte[] {1, 0});
		IpfsFile post1 = _storeRecord(data, "post1", null, new byte[] {1, 1});
		IpfsFile post2 = _storeRecord(data, "post2", null, null);
		IpfsFile post3 = _storeRecord(data, "post3", new byte[] {0, 3}, null);
		IpfsFile post4 = _storeRecord(data, "post4", new byte[] {0, 4}, new byte[] {1, 4});
		IpfsFile post5 = _storeRecord(data, "post5", new byte[] {0, 5}, new byte[] {1, 5});
		IpfsFile post6 = _storeRecord(data, "post6", null, new byte[] {1, 6});
		IpfsFile post7 = _storeRecord(data, "post7", null, null);
		index = _addElementToStream(data, index, post0);
		index = _addElementToStream(data, index, post1);
		index = _addElementToStream(data, index, post2);
		index = _addElementToStream(data, index, post3);
		index = _addElementToStream(data, index, post4);
		index = _addElementToStream(data, index, post5);
		index = _addElementToStream(data, index, post6);
		
		// Now do the first part of the sync.
		PrefsData prefs = PrefsData.defaultPrefs();
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		// First sync doesn't fetch records so run again.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		oldIndexElement = newIndexElement;
		FollowingCacheElement[] result = testSupport.getList();
		// We should see the 5 elements.
		Assert.assertEquals(5, result.length);
		Assert.assertEquals(post2, result[0].elementHash());
		Assert.assertEquals(post3, result[1].elementHash());
		Assert.assertEquals(post4, result[2].elementHash());
		Assert.assertEquals(post5, result[3].elementHash());
		Assert.assertEquals(post6, result[4].elementHash());
		// We should see all the records observed, but only the 5 limit pinned.
		Assert.assertEquals(7, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(FolloweeRefreshLogic.INCREMENTAL_RECORD_COUNT, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
		
		// Remove 0 and 6, then add 7 (1 will be the pivot so we will make that a different test).
		index = _removeElementFromStream(data, index, post0);
		index = _removeElementFromStream(data, index, post6);
		index = _addElementToStream(data, index, post7);
		
		// Refreshing after this change should see the remaining elements.
		oldIndexElement = newIndexElement;
		newIndexElement = index;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		result = testSupport.getList();
		// We will see 6 posts since we will see all 7 except for post 0 and 6, since they were removed
		Assert.assertEquals(6, result.length);
		Assert.assertEquals(post2, result[0].elementHash());
		Assert.assertEquals(post3, result[1].elementHash());
		Assert.assertEquals(post4, result[2].elementHash());
		Assert.assertEquals(post5, result[3].elementHash());
		Assert.assertEquals(post1, result[4].elementHash());
		Assert.assertEquals(post7, result[5].elementHash());
		// We observe 7 being added.
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		// We pinned 1 and 7.
		Assert.assertEquals(2, testSupport.getAndClearNewRecordsPinned().length);
		// We observed 0 and 6 disappearing.
		Assert.assertEquals(2, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
		
		// Nothing else should change here.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		result = testSupport.getList();
		Assert.assertEquals(6, result.length);
		Assert.assertEquals(post2, result[0].elementHash());
		Assert.assertEquals(post3, result[1].elementHash());
		Assert.assertEquals(post4, result[2].elementHash());
		Assert.assertEquals(post5, result[3].elementHash());
		Assert.assertEquals(post1, result[4].elementHash());
		Assert.assertEquals(post7, result[5].elementHash());
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
	}

	@Test
	public void startFollowRemovePivot() throws Throwable
	{
		// We want to create a user which will take 2 incremental synchronizations to begin following.
		// We will then remove the pivot point and make sure that the synchronization can handle that.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		IpfsFile post0 = _storeRecord(data, "post0", new byte[] {0, 0}, new byte[] {1, 0});
		IpfsFile post1 = _storeRecord(data, "post1", null, new byte[] {1, 1});
		IpfsFile post2 = _storeRecord(data, "post2", null, null);
		IpfsFile post3 = _storeRecord(data, "post3", new byte[] {0, 3}, null);
		IpfsFile post4 = _storeRecord(data, "post4", new byte[] {0, 4}, new byte[] {1, 4});
		IpfsFile post5 = _storeRecord(data, "post5", new byte[] {0, 5}, new byte[] {1, 5});
		IpfsFile post6 = _storeRecord(data, "post6", null, new byte[] {1, 6});
		IpfsFile post7 = _storeRecord(data, "post7", null, null);
		index = _addElementToStream(data, index, post0);
		index = _addElementToStream(data, index, post1);
		index = _addElementToStream(data, index, post2);
		index = _addElementToStream(data, index, post3);
		index = _addElementToStream(data, index, post4);
		index = _addElementToStream(data, index, post5);
		index = _addElementToStream(data, index, post6);
		index = _addElementToStream(data, index, post7);
		
		// Do the first step in the sync.
		PrefsData prefs = PrefsData.defaultPrefs();
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		// First sync doesn't fetch records so run again.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		FollowingCacheElement[] result = testSupport.getList();
		// We should see 5 posts.
		Assert.assertEquals(FolloweeRefreshLogic.INCREMENTAL_RECORD_COUNT, result.length);
		Assert.assertEquals(post3, result[0].elementHash());
		Assert.assertEquals(post4, result[1].elementHash());
		Assert.assertEquals(post5, result[2].elementHash());
		Assert.assertEquals(post6, result[3].elementHash());
		Assert.assertEquals(post7, result[4].elementHash());
		// We should see all the records observed, but only the 5 limit pinned.
		Assert.assertEquals(8, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(FolloweeRefreshLogic.INCREMENTAL_RECORD_COUNT, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
		
		// Remove 2 (the pivot) and verify that the incremental sync figures this out.
		index = _removeElementFromStream(data, index, post2);
		
		// In the next refresh, this will be a forward refresh, not yet picking up element 1 or 0.
		oldIndexElement = newIndexElement;
		newIndexElement = index;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		result = testSupport.getList();
		// We should see everything else.
		Assert.assertEquals(7, result.length);
		Assert.assertEquals(post3, result[0].elementHash());
		Assert.assertEquals(post4, result[1].elementHash());
		Assert.assertEquals(post5, result[2].elementHash());
		Assert.assertEquals(post6, result[3].elementHash());
		Assert.assertEquals(post7, result[4].elementHash());
		Assert.assertEquals(post0, result[5].elementHash());
		Assert.assertEquals(post1, result[6].elementHash());
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(2, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(1, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
		
		// Nothing should change.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		result = testSupport.getList();
		Assert.assertEquals(7, result.length);
		Assert.assertEquals(post3, result[0].elementHash());
		Assert.assertEquals(post4, result[1].elementHash());
		Assert.assertEquals(post5, result[2].elementHash());
		Assert.assertEquals(post6, result[3].elementHash());
		Assert.assertEquals(post7, result[4].elementHash());
		Assert.assertEquals(post0, result[5].elementHash());
		Assert.assertEquals(post1, result[6].elementHash());
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
	}

	@Test
	public void saturateCache() throws Throwable
	{
		// Make sure that we still see a cached record, even if not everything is selected for caching.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		IpfsFile post0 = _storeRecord(data, "post0", new byte[] {0, 0}, new byte[] {1, 0});
		IpfsFile post1 = _storeRecord(data, "post1", new byte[] {0, 1}, new byte[] {1, 1});
		IpfsFile post2 = _storeRecord(data, "post2", new byte[] {0, 2}, new byte[] {1, 2});
		IpfsFile post3 = _storeRecord(data, "post3", new byte[] {0, 3}, new byte[] {1, 3});
		IpfsFile post4 = _storeRecord(data, "post4", new byte[] {0, 4}, new byte[] {1, 4});
		IpfsFile post5 = _storeRecord(data, "post5", new byte[] {0, 5}, new byte[] {1, 5});
		IpfsFile post6 = _storeRecord(data, "post6", new byte[] {0, 6}, new byte[] {1, 6});
		index = _addElementToStream(data, index, post0);
		index = _addElementToStream(data, index, post1);
		index = _addElementToStream(data, index, post2);
		index = _addElementToStream(data, index, post3);
		index = _addElementToStream(data, index, post4);
		index = _addElementToStream(data, index, post5);
		index = _addElementToStream(data, index, post6);
		
		// Do the first step in the sync.
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.followeeCacheTargetBytes = 10L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		// First sync doesn't fetch records so run again.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		FollowingCacheElement[] result = testSupport.getList();
		// We should see 5 posts.
		Assert.assertEquals(FolloweeRefreshLogic.INCREMENTAL_RECORD_COUNT, result.length);
		Assert.assertEquals(post2, result[0].elementHash());
		Assert.assertEquals(post3, result[1].elementHash());
		Assert.assertEquals(post4, result[2].elementHash());
		Assert.assertEquals(post5, result[3].elementHash());
		Assert.assertEquals(post6, result[4].elementHash());
		// We should see all the records observed, but only the 5 limit pinned.
		Assert.assertEquals(7, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(FolloweeRefreshLogic.INCREMENTAL_RECORD_COUNT, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
		
		// Refresh again to see the remainder.
		oldIndexElement = newIndexElement;
		FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(7, result.length);
		Assert.assertEquals(post2, result[0].elementHash());
		Assert.assertEquals(post3, result[1].elementHash());
		Assert.assertEquals(post4, result[2].elementHash());
		Assert.assertEquals(post5, result[3].elementHash());
		Assert.assertEquals(post6, result[4].elementHash());
		Assert.assertEquals(post0, result[5].elementHash());
		Assert.assertEquals(post1, result[6].elementHash());
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(2, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
	}

	@Test
	public void duplicatedRecords() throws Throwable
	{
		// We want to create a user which has duplicated entries, some of them failing to load, to make sure that this doesn't confuse the sync logic.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		
		byte[] thumb0 = new byte[] {0, 0};
		byte[] video0 = new byte[] {1, 0};
		byte[] video1 = new byte[] {1, 1};
		IpfsFile post0 = _storeRecord(data, "post0", thumb0, video0);
		IpfsFile post1 = _storeRecord(data, "post1", null, video1);
		IpfsFile post2 = _storeRecord(data, "post2", null, null);
		
		// Create a stream with a few duplicates.
		index = _addElementToStream(data, index, post0);
		index = _addElementToStream(data, index, post1);
		index = _addElementToStream(data, index, post1);
		index = _addElementToStream(data, index, post2);
		index = _addElementToStream(data, index, post0);
		index = _addElementToStream(data, index, post2);
		index = _addElementToStream(data, index, post1);
		
		// Break a few records and leaves in a temporary way.
		byte[] dataVideo1 = data.remove(MockSingleNode.generateHash(video1));
		byte[] dataPost2 = data.remove(post2);
		
		// Now, synchronize twice.
		PrefsData prefs = PrefsData.defaultPrefs();
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		// First sync doesn't fetch records so run again.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		FollowingCacheElement[] result = testSupport.getList();
		// We should see post0 and post1 (without leaves), but post2 is missing.
		Assert.assertEquals(2, result.length);
		Assert.assertEquals(post0, result[0].elementHash());
		Assert.assertEquals(MockSingleNode.generateHash(thumb0), result[0].imageHash());
		Assert.assertEquals(MockSingleNode.generateHash(video0), result[0].leafHash());
		Assert.assertEquals(post1, result[1].elementHash());
		Assert.assertNull(result[1].imageHash());
		Assert.assertNull(result[1].leafHash());
		// We shouldn't see duplicates in the observed.
		Assert.assertEquals(3, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(2, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		// Verify the temporary skips.
		Assert.assertEquals(1, testSupport.temporarySkips.size());
		Assert.assertEquals(post2, testSupport.temporarySkips.get(0));
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		
		// Add another duplicate and fix the data before resyncing again.
		data.put(MockSingleNode.generateHash(video1), dataVideo1);
		data.put(post2, dataPost2);
		index = _addElementToStream(data, index, post0);
		index = _addElementToStream(data, index, post2);
		index = _addElementToStream(data, index, post1);
		
		// We should see the previous failure processed since it was restored.
		oldIndexElement = newIndexElement;
		newIndexElement = index;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(3, result.length);
		Assert.assertEquals(post0, result[0].elementHash());
		Assert.assertEquals(post1, result[1].elementHash());
		Assert.assertEquals(post2, result[2].elementHash());
		
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
	}

	@Test
	public void removeDuplicates() throws Throwable
	{
		// We want to create a user which has duplicated entries, and show how we report adding and removing them.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		
		byte[] thumb0 = new byte[] {0, 0};
		byte[] video0 = new byte[] {1, 0};
		byte[] video1 = new byte[] {1, 1};
		IpfsFile post0 = _storeRecord(data, "post0", thumb0, video0);
		IpfsFile post1 = _storeRecord(data, "post1", null, video1);
		IpfsFile post2 = _storeRecord(data, "post2", null, null);
		
		// Create a stream with a few duplicates.
		index = _addElementToStream(data, index, post0);
		index = _addElementToStream(data, index, post1);
		index = _addElementToStream(data, index, post1);
		index = _addElementToStream(data, index, post0);
		index = _addElementToStream(data, index, post1);
		
		// Start following and then synchronize to verify that we see both.
		PrefsData prefs = PrefsData.defaultPrefs();
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		TestSupport testSupport = new TestSupport(data, originalElements);
		boolean moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertTrue(moreToDo);
		// First sync doesn't fetch records so run again.
		oldIndexElement = newIndexElement;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertFalse(moreToDo);
		FollowingCacheElement[] result = testSupport.getList();
		// We should see both.
		Assert.assertEquals(2, result.length);
		Assert.assertEquals(post0, result[0].elementHash());
		Assert.assertEquals(post1, result[1].elementHash());
		// We shouldn't see duplicates in the observed.
		Assert.assertEquals(2, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(2, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.temporarySkips.size());
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		
		// Add another duplicate and some new posts before resyncing again.
		index = _addElementToStream(data, index, post2);
		index = _addElementToStream(data, index, post1);
		index = _addElementToStream(data, index, post2);
		
		oldIndexElement = newIndexElement;
		newIndexElement = index;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(3, result.length);
		Assert.assertEquals(post0, result[0].elementHash());
		Assert.assertEquals(post1, result[1].elementHash());
		Assert.assertEquals(post2, result[2].elementHash());
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(1, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
		
		// Remove one reference to each and verify that nothing changes.
		index = _removeElementFromStream(data, index, post0);
		index = _removeElementFromStream(data, index, post1);
		index = _removeElementFromStream(data, index, post2);
		oldIndexElement = newIndexElement;
		newIndexElement = index;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(3, result.length);
		Assert.assertEquals(post0, result[0].elementHash());
		Assert.assertEquals(post1, result[1].elementHash());
		Assert.assertEquals(post2, result[2].elementHash());
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(0, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
		
		// Remove one reference to each, again, and verify that post0 and post0 were removed.
		index = _removeElementFromStream(data, index, post0);
		index = _removeElementFromStream(data, index, post1);
		index = _removeElementFromStream(data, index, post2);
		oldIndexElement = newIndexElement;
		newIndexElement = index;
		moreToDo = FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		result = testSupport.getList();
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(post1, result[0].elementHash());
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsObserved().length);
		Assert.assertEquals(0, testSupport.getAndClearNewRecordsPinned().length);
		Assert.assertEquals(2, testSupport.getAndClearRecordsDisappeared().length);
		Assert.assertEquals(0, testSupport.permanentSkips.size());
		Assert.assertEquals(0, testSupport.temporarySkips.size());
	}


	private void _commonSizeCheck(Map<IpfsFile, byte[]> data, IpfsFile indexHash) throws IpfsConnectionException, FailedDeserializationException
	{
		PrefsData prefs = PrefsData.defaultPrefs();
		prefs.videoEdgePixelMax = 1280;
		prefs.followeeCacheTargetBytes = 100L;
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = indexHash;
		long currentCacheUsageInBytes = 0L;
		boolean didFail = false;
		try
		{
			TestSupport testSupport = new TestSupport(data, originalElements);
			FolloweeRefreshLogic.refreshFollowee(testSupport, prefs, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
			// If the start was a success, we should see the name.
			Assert.assertEquals("name", testSupport.lastName);
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
		IpfsFile userPicFile = MockSingleNode.generateHash(userPic);
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
		IpfsFile recommendationsHash = MockSingleNode.generateHash(serialized);
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
		IpfsFile descriptionHash = MockSingleNode.generateHash(serialized);
		data.put(descriptionHash, serialized);
		return descriptionHash;
	}

	private static IpfsFile _store_StreamRecords(Map<IpfsFile, byte[]> data) throws SizeConstraintException
	{
		byte[] serialized;
		StreamRecords records = new StreamRecords();
		serialized = GlobalData.serializeRecords(records);
		IpfsFile recordsHash = MockSingleNode.generateHash(serialized);
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
		IpfsFile indexHash = MockSingleNode.generateHash(serialized);
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
		record.setPublisherKey(MockKeys.K1.toPublicKey());
		DataArray array = new DataArray();
		if (null != thumbnail)
		{
			IpfsFile thumbnailHash = MockSingleNode.generateHash(thumbnail);
			data.put(thumbnailHash, thumbnail);
			DataElement element = new DataElement();
			element.setMime("image/jpeg");
			element.setSpecial(ElementSpecialType.IMAGE);
			element.setCid(thumbnailHash.toSafeString());
			array.getElement().add(element);
		}
		if (null != video)
		{
			IpfsFile videoHash = MockSingleNode.generateHash(video);
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
		IpfsFile recordHash = MockSingleNode.generateHash(serialized);
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
		recordsHash = MockSingleNode.generateHash(serialized);
		Assert.assertNull(data.put(recordsHash, serialized));
		
		index.setRecords(recordsHash.toSafeString());
		serialized = GlobalData.serializeIndex(index);
		Assert.assertNotNull(data.remove(indexHash));
		indexHash = MockSingleNode.generateHash(serialized);
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
		recordsHash = MockSingleNode.generateHash(serialized);
		Assert.assertNull(data.put(recordsHash, serialized));
		
		index.setRecords(recordsHash.toSafeString());
		serialized = GlobalData.serializeIndex(index);
		Assert.assertNotNull(data.remove(indexHash));
		indexHash = MockSingleNode.generateHash(serialized);
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


	private static class TestSupport implements FolloweeRefreshLogic.IRefreshSupport
	{
		private final Map<IpfsFile, byte[]> _upstreamData;
		private final List<FollowingCacheElement> _list;
		private final Map<IpfsFile, byte[]> _data = new HashMap<>();
		private final List<IpfsFile> _deferredMetaUnpin = new ArrayList<>();
		private final List<IpfsFile> _deferredFileUnpin = new ArrayList<>();
		private final Map<IpfsFile, Integer> _metaDataPinCount = new HashMap<>();
		private final Map<IpfsFile, Integer> _filePinCount = new HashMap<>();
		private final List<IpfsFile> _newRecordsObserved = new ArrayList<>();
		private final List<IpfsFile> _newRecordsPinned = new ArrayList<>();
		private final List<IpfsFile> _existingRecordsDisappeared = new ArrayList<>();
		
		// Miscellaneous other checks.
		public String lastName;
		public List<IpfsFile> permanentSkips = new ArrayList<>();
		public List<IpfsFile> temporarySkips = new ArrayList<>();
		
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
		public IpfsFile[] getAndClearNewRecordsObserved()
		{
			IpfsFile[] array = _newRecordsObserved.toArray((int size) -> new IpfsFile[size]);
			_newRecordsObserved.clear();
			return array;
		}
		public IpfsFile[] getAndClearNewRecordsPinned()
		{
			IpfsFile[] array = _newRecordsPinned.toArray((int size) -> new IpfsFile[size]);
			_newRecordsPinned.clear();
			return array;
		}
		public IpfsFile[] getAndClearRecordsDisappeared()
		{
			IpfsFile[] array = _existingRecordsDisappeared.toArray((int size) -> new IpfsFile[size]);
			_existingRecordsDisappeared.clear();
			return array;
		}
		public void clearAfterFailure()
		{
			_deferredMetaUnpin.clear();
			_deferredFileUnpin.clear();
			_list.clear();
			_newRecordsObserved.clear();
			_newRecordsPinned.clear();
			_existingRecordsDisappeared.clear();
		}
		@Override
		public void logMessageImportant(String message)
		{
			// No logging in tests.
		}
		@Override
		public void logMessageVerbose(String message)
		{
			// No logging in tests.
		}
		@Override
		public void followeeDescriptionNewOrUpdated(AbstractDescription description)
		{
			this.lastName = description.getName();
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
			_deferredMetaUnpin.add(cid);
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
		public FollowingCacheElement getCacheDataForElement(IpfsFile elementHash)
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
			return match;
		}
		@Override
		public void addRecordForFollowee(IpfsFile elementHash)
		{
			_newRecordsObserved.add(elementHash);
		}
		@Override
		public void cacheRecordForFollowee(IpfsFile elementHash
				, AbstractRecord recordData
				, IpfsFile imageHash
				, IpfsFile audioLeaf
				, IpfsFile videoLeaf
				, int videoEdgeSize
				, long combinedLeafSizeBytes
		)
		{
			_newRecordsPinned.add(elementHash);
			IpfsFile leafHash = (null != audioLeaf) ? audioLeaf : videoLeaf;
			_list.add(new FollowingCacheElement(elementHash, imageHash, leafHash, combinedLeafSizeBytes));
		}
		@Override
		public void removeRecordForFollowee(IpfsFile elementHash)
		{
			_existingRecordsDisappeared.add(elementHash);
		}
		@Override
		public void removeElementFromCache(IpfsFile elementHash, AbstractRecord recordData, IpfsFile imageHash, IpfsFile audioHash, IpfsFile videoHash, int videoEdgeSize)
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
			Assert.assertTrue(match >= 0);
			_list.remove(match);
		}
		@Override
		public void addSkippedRecord(IpfsFile recordCid, boolean isPermanent)
		{
			if (isPermanent)
			{
				this.permanentSkips.add(recordCid);
			}
			else
			{
				this.temporarySkips.add(recordCid);
			}
		}
		@Override
		public boolean hasRecordBeenProcessed(IpfsFile recordCid)
		{
			return _list.stream().anyMatch((FollowingCacheElement elt) -> elt.elementHash().equals(recordCid))
					|| this.permanentSkips.contains(recordCid)
					|| this.temporarySkips.contains(recordCid)
			;
		}
		@Override
		public IpfsFile getAndResetNextTemporarySkip()
		{
			return this.temporarySkips.isEmpty()
					? null
					: this.temporarySkips.remove(0)
			;
		}
	}
}
