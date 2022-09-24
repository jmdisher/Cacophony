package com.jeffdisher.cacophony.logic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import io.ipfs.cid.Cid;


public class TestFolloweeRefreshLogic
{
	private static final IpfsKey DUMMY_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testNewEmptyFollow() throws Throwable
	{
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		TestSupport testSupport = new TestSupport(data);
		GlobalPrefs globalPrefs = new GlobalPrefs(1280, 5L);
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		FollowingCacheElement[] result = FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(0, result.length);
	}

	@Test
	public void testNewSingleFollowNoLeaf() throws Throwable
	{
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		index = _addElementToStream(data, index, "Name", null, null);
		TestSupport testSupport = new TestSupport(data);
		GlobalPrefs globalPrefs = new GlobalPrefs(1280, 5L);
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		FollowingCacheElement[] result = FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(1, result.length);
	}

	@Test
	public void testNewSingleFollowSmallLeaf() throws Throwable
	{
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		index = _addElementToStream(data, index, "Name", new byte[] {1}, new byte[] {1, 2});
		TestSupport testSupport = new TestSupport(data);
		GlobalPrefs globalPrefs = new GlobalPrefs(1280, 5L);
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		FollowingCacheElement[] result = FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(3, result[0].combinedSizeBytes());
	}

	@Test
	public void testStopFollow() throws Throwable
	{
		// First, start following.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		index = _addElementToStream(data, index, "Name", null, null);
		TestSupport testSupport = new TestSupport(data);
		GlobalPrefs globalPrefs = new GlobalPrefs(1280, 5L);
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		FollowingCacheElement[] result = FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(1, result.length);
		
		// Now, stop following.
		originalElements = result;
		oldIndexElement = index;
		newIndexElement = null;
		currentCacheUsageInBytes = 0L;
		result = FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(0, result.length);
	}

	@Test
	public void testRefreshNoLeaves() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		TestSupport testSupport = new TestSupport(data);
		GlobalPrefs globalPrefs = new GlobalPrefs(1280, 5L);
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		FollowingCacheElement[] result = FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(0, result.length);
		
		// Now, add an element with no leaves and refresh.
		IpfsFile oldIndex = index;
		index = _addElementToStream(data, index, "Name", null, null);
		originalElements = result;
		oldIndexElement = oldIndex;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		result = FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(0, result[0].combinedSizeBytes());
	}

	@Test
	public void testRefreshSmallLeaves() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		TestSupport testSupport = new TestSupport(data);
		GlobalPrefs globalPrefs = new GlobalPrefs(1280, 5L);
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		FollowingCacheElement[] result = FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(0, result.length);
		
		// Now, add an element with small leaves and refresh.
		IpfsFile oldIndex = index;
		index = _addElementToStream(data, index, "Name", new byte[] {1}, new byte[] {1, 2});
		originalElements = result;
		oldIndexElement = oldIndex;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		result = FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(1, result.length);
		Assert.assertEquals(3, result[0].combinedSizeBytes());
	}

	@Test
	public void testRefreshHugeLeaves() throws Throwable
	{
		// Start following an empty user.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		TestSupport testSupport = new TestSupport(data);
		GlobalPrefs globalPrefs = new GlobalPrefs(1280, 5L);
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		FollowingCacheElement[] result = FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(0, result.length);
		
		// Now, add an element with big leaves and refresh.
		IpfsFile oldIndex = index;
		index = _addElementToStream(data, index, "Name", new byte[] {1, 2, 3, 4, 5, 6}, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
		originalElements = result;
		oldIndexElement = oldIndex;
		newIndexElement = index;
		currentCacheUsageInBytes = 0L;
		result = FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(1, result.length);
		// NOTE:  Since there is only one element, it is currently allowed to overflow the cache.
		// TODO:  Change this when we apply this limit.
		Assert.assertEquals(16, result[0].combinedSizeBytes());
	}

	@Test
	public void testBrokenMetaData() throws Throwable
	{
		// Create a normal user with a single record.
		Map<IpfsFile, byte[]> data = new HashMap<>();
		IpfsFile index = _buildEmptyUser(data);
		index = _addElementToStream(data, index, "Name", null, null);
		// Now, break the recommendations (just misc meta-data).
		StreamIndex indexObject = GlobalData.deserializeIndex(data.get(index));
		data.remove(IpfsFile.fromIpfsCid(indexObject.getRecommendations()));
		TestSupport testSupport = new TestSupport(data);
		GlobalPrefs globalPrefs = new GlobalPrefs(1280, 5L);
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		
		// We expect a failure if we can't pin a meta-data element for network reasons.
		boolean didFail = false;
		try
		{
			FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
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
		index = _addElementToStream(data, index, "Name", null, null);
		// Now, break the record by dropping it form storage.
		IpfsFile[] records = _readRecordHashes(data, index);
		Assert.assertEquals(1, records.length);
		data.remove(records[0]);
		TestSupport testSupport = new TestSupport(data);
		GlobalPrefs globalPrefs = new GlobalPrefs(1280, 5L);
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		
		// We expect a failure if we can't pin a meta-data element for network reasons.
		boolean didFail = false;
		try
		{
			FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
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
		index = _addElementToStream(data, index, "Name", new byte[] {1}, new byte[] { 1, 2, 3 });
		
		// Now, break the leaf by dropping it form storage.
		IpfsFile[] records = _readRecordHashes(data, index);
		Assert.assertEquals(1, records.length);
		StreamRecord record = GlobalData.deserializeRecord(data.get(records[0]));
		List<DataElement> leaves = record.getElements().getElement();
		Assert.assertEquals(2, leaves.size());
		IpfsFile leafCid = IpfsFile.fromIpfsCid(leaves.get(0).getCid());
		data.remove(leafCid);
		
		TestSupport testSupport = new TestSupport(data);
		GlobalPrefs globalPrefs = new GlobalPrefs(1280, 5L);
		FollowingCacheElement[] originalElements = new FollowingCacheElement[0];
		IpfsFile oldIndexElement = null;
		IpfsFile newIndexElement = index;
		long currentCacheUsageInBytes = 0L;
		
		// We expect that this will succeed, since it isn't a meta-data failure, but we will decide NOT to cache this element.
		FollowingCacheElement[] result = FolloweeRefreshLogic.refreshFollowee(testSupport, globalPrefs, originalElements, oldIndexElement, newIndexElement, currentCacheUsageInBytes);
		Assert.assertEquals(0, result.length);
	}


	private static IpfsFile _buildEmptyUser(Map<IpfsFile, byte[]> data)
	{
		// We build a fake user.
		byte[] userPic = new byte[] {'a','b','c'};
		IpfsFile userPicFile = _fakeHash(userPic);
		data.put(userPicFile, userPic);
		
		StreamRecommendations recommendations = new StreamRecommendations();
		byte[] serialized = GlobalData.serializeRecommendations(recommendations);
		IpfsFile recommendationsHash = _fakeHash(serialized);
		data.put(recommendationsHash, serialized);
		
		StreamDescription description = new StreamDescription();
		description.setName("name");
		description.setDescription("description");
		description.setPicture(userPicFile.toSafeString());
		serialized = GlobalData.serializeDescription(description);
		IpfsFile descriptionHash = _fakeHash(serialized);
		data.put(descriptionHash, serialized);
		
		StreamRecords records = new StreamRecords();
		serialized = GlobalData.serializeRecords(records);
		IpfsFile recordsHash = _fakeHash(serialized);
		data.put(recordsHash, serialized);
		
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

	private static IpfsFile _addElementToStream(Map<IpfsFile, byte[]> data, IpfsFile indexHash, String name, byte[] thumbnail, byte[] video)
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
		
		// Read the existing stream.
		StreamIndex index = GlobalData.deserializeIndex(data.get(indexHash));
		IpfsFile recordsHash = IpfsFile.fromIpfsCid(index.getRecords());
		StreamRecords records = GlobalData.deserializeRecords(data.get(recordsHash));
		records.getRecord().add(recordHash.toSafeString());
		serialized = GlobalData.serializeRecords(records);
		recordsHash = _fakeHash(serialized);
		Assert.assertNull(data.put(recordsHash, serialized));
		
		index.setRecords(recordsHash.toSafeString());
		serialized = GlobalData.serializeIndex(index);
		indexHash = _fakeHash(serialized);
		Assert.assertNull(data.put(indexHash, serialized));
		return indexHash;
	}

	private IpfsFile[] _readRecordHashes(Map<IpfsFile, byte[]> data, IpfsFile indexHash)
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


	private static class TestSupport implements FolloweeRefreshLogic.IRefreshSupport
	{
		private final Map<IpfsFile, byte[]> _upstreamData;
		private final Map<IpfsFile, byte[]> _data = new HashMap<>();
		private final Map<IpfsFile, Integer> _metaDataPinCount = new HashMap<>();
		private final Map<IpfsFile, Integer> _filePinCount = new HashMap<>();
		
		public TestSupport(Map<IpfsFile, byte[]> upstreamData)
		{
			_upstreamData = upstreamData;
		}
		
		@Override
		public void logMessage(String message)
		{
			System.out.println(message);
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
			FuturePin future = new FuturePin();
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
		public void removeMetaDataFromFollowCache(IpfsFile cid)
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
		@Override
		public FuturePin addFileToFollowCache(IpfsFile cid)
		{
			FuturePin future = new FuturePin();
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
		public void removeFileFromFollowCache(IpfsFile cid)
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
		@Override
		public <R> FutureRead<R> loadCached(IpfsFile file, Function<byte[], R> decoder)
		{
			Assert.assertTrue(_data.containsKey(file));
			FutureRead<R> future = new FutureRead<R>();
			future.success(decoder.apply(_data.get(file)));
			return future;
		}
	}
}
