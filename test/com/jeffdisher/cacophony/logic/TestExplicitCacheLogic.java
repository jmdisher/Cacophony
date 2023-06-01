package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;

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
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;


public class TestExplicitCacheLogic
{
	@Test
	public void userNotFound() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		int startPin = _countPins(access);
		boolean didFail;
		try
		{
			ExplicitCacheLogic.loadUserInfo(access, MockKeys.K1);
			didFail = false;
		}
		catch (KeyException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		int endPin = _countPins(access);
		Assert.assertEquals(startPin, endPin);
		// We failed to resolve so we shouldn't read anything.
		Assert.assertEquals(0, access.sizeChecksPerformed);
		Assert.assertEquals(0, access.sizeAndReadPerformed);
	}

	@Test
	public void foundUser() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		_populateWithEmpty(access, MockKeys.K1, "userPic".getBytes());
		ExplicitCacheData.UserInfo userInfo = ExplicitCacheLogic.loadUserInfo(access, MockKeys.K1);
		Assert.assertNotNull(userInfo);
		// While we pin all for elements (index, recommendations, description, picture), we don't actually load the picture.
		Assert.assertEquals(3, access.sizeAndReadPerformed);
		// While ForeignChannelReader implicitly does size checks, those are below the Access interface level so we only see the explicit checks on pic, index, description, and recommendations.
		Assert.assertEquals(4, access.sizeChecksPerformed);
	}

	@Test
	public void missingUserPic() throws Throwable
	{
		// Make sure that missing data causes this to fail and leave the pin counts unchanged.
		MockWritingAccess access = new MockWritingAccess();
		byte[] userPic = "userPic".getBytes();
		_populateWithEmpty(access, MockKeys.K1, userPic);
		access.unpin(MockSingleNode.generateHash(userPic));
		int startPin = _countPins(access);
		boolean didFail;
		try
		{
			ExplicitCacheLogic.loadUserInfo(access, MockKeys.K1);
			didFail = false;
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		int endPin = _countPins(access);
		Assert.assertEquals(startPin, endPin);
		// We only explicitly check the size of the pic, but read and check index, description, recommendations.
		Assert.assertEquals(1, access.sizeChecksPerformed);
		Assert.assertEquals(3, access.sizeAndReadPerformed);
		
	}

	@Test
	public void missingRecommendations() throws Throwable
	{
		// Make sure that missing data causes this to fail and leave the pin counts unchanged.
		MockWritingAccess access = new MockWritingAccess();
		_populateWithEmpty(access, MockKeys.K1, "userPic".getBytes());
		access.unpin(MockSingleNode.generateHash(GlobalData.serializeRecommendations(new StreamRecommendations())));
		int startPin = _countPins(access);
		boolean didFail;
		try
		{
			ExplicitCacheLogic.loadUserInfo(access, MockKeys.K1);
			didFail = false;
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		int endPin = _countPins(access);
		Assert.assertEquals(startPin, endPin);
		// We don't check the sizes since we fail in the pins.
		Assert.assertEquals(0, access.sizeChecksPerformed);
		// Read index, description, recommendations.
		Assert.assertEquals(3, access.sizeAndReadPerformed);
	}

	@Test
	public void recordNotFound() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		int startPin = _countPins(access);
		boolean didFail;
		try
		{
			ExplicitCacheLogic.loadRecordInfo(access, MockSingleNode.generateHash(new byte[] {1}));
			didFail = false;
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		int endPin = _countPins(access);
		Assert.assertEquals(startPin, endPin);
		// We use the single check which combines these.
		Assert.assertEquals(0, access.sizeChecksPerformed);
		Assert.assertEquals(1, access.sizeAndReadPerformed);
	}

	@Test
	public void noLeafRecord() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		IpfsFile cid = _populateStreamRecord(access, MockKeys.K1, "name", null, null, 0, null);
		CachedRecordInfo record = ExplicitCacheLogic.loadRecordInfo(access, cid);
		Assert.assertNotNull(record);
		// We just see the one size check and load.
		Assert.assertEquals(1, access.sizeChecksPerformed);
		Assert.assertEquals(1, access.sizeAndReadPerformed);
		// A second attempt should be a cache hit and not touch the network.
		CachedRecordInfo record2 = ExplicitCacheLogic.loadRecordInfo(access, cid);
		Assert.assertNotNull(record2);
		Assert.assertEquals(1, access.sizeChecksPerformed);
		Assert.assertEquals(1, access.sizeAndReadPerformed);
	}

	@Test
	public void videoRecord() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		IpfsFile cid = _populateStreamRecord(access, MockKeys.K1, "name", "thumb".getBytes(), "video".getBytes(), 10, null);
		CachedRecordInfo record = ExplicitCacheLogic.loadRecordInfo(access, cid);
		Assert.assertNotNull(record);
		// We just see the 3 size checks and the record load.
		Assert.assertEquals(3, access.sizeChecksPerformed);
		Assert.assertEquals(1, access.sizeAndReadPerformed);
		// A second attempt should be a cache hit and not touch the network.
		CachedRecordInfo record2 = ExplicitCacheLogic.loadRecordInfo(access, cid);
		Assert.assertNotNull(record2);
		Assert.assertEquals(3, access.sizeChecksPerformed);
		Assert.assertEquals(1, access.sizeAndReadPerformed);
	}

	@Test
	public void audioRecord() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		IpfsFile cid = _populateStreamRecord(access, MockKeys.K1, "name", null, null, 0, "audio".getBytes());
		CachedRecordInfo record = ExplicitCacheLogic.loadRecordInfo(access, cid);
		Assert.assertNotNull(record);
		// We just see the 2 size checks and the record load.
		Assert.assertEquals(2, access.sizeChecksPerformed);
		Assert.assertEquals(1, access.sizeAndReadPerformed);
		// A second attempt should be a cache hit and not touch the network.
		CachedRecordInfo record2 = ExplicitCacheLogic.loadRecordInfo(access, cid);
		Assert.assertNotNull(record2);
		Assert.assertEquals(2, access.sizeChecksPerformed);
		Assert.assertEquals(1, access.sizeAndReadPerformed);
	}

	@Test
	public void brokenRecordLeaf() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		// Break the video leaf and make sure we fail and don't change pin counts.
		byte[] videoData = "video".getBytes();
		IpfsFile cid = _populateStreamRecord(access, MockKeys.K1, "name", "thumb".getBytes(), videoData, 10, null);
		access.unpin(MockSingleNode.generateHash(videoData));
		int startPin = _countPins(access);
		boolean didFail;
		try
		{
			ExplicitCacheLogic.loadRecordInfo(access, cid);
			didFail = false;
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		int endPin = _countPins(access);
		Assert.assertEquals(startPin, endPin);
		// We only read the record and check the size of the record before accessing the leaves.  We check the size after pin success so we don't check the video size.
		Assert.assertEquals(2, access.sizeChecksPerformed);
		Assert.assertEquals(1, access.sizeAndReadPerformed);
	}


	private static void _populateWithEmpty(MockWritingAccess access, IpfsKey publishKey, byte[] userPic) throws Throwable
	{
		StreamDescription desc = new StreamDescription();
		desc.setName("name");
		desc.setDescription("description");
		desc.setPicture(_storeWithString(access, userPic));
		StreamRecords records = new StreamRecords();
		StreamRecommendations recom = new StreamRecommendations();
		StreamIndex index = new StreamIndex();
		index.setVersion(1);
		index.setDescription(_storeWithString(access, GlobalData.serializeDescription(desc)));
		index.setRecords(_storeWithString(access, GlobalData.serializeRecords(records)));
		index.setRecommendations(_storeWithString(access, GlobalData.serializeRecommendations(recom)));
		IpfsFile root = _storeData(access, GlobalData.serializeIndex(index));
		access.oneKey = publishKey;
		access.oneRoot = root;
	}

	private static IpfsFile _populateStreamRecord(MockWritingAccess access, IpfsKey publisherKey, String title, byte[] thumb, byte[] video, int videoDimensions, byte[] audio) throws Throwable
	{
		DataArray elements = new DataArray();
		if (null != thumb)
		{
			DataElement element = new DataElement();
			element.setSpecial(ElementSpecialType.IMAGE);
			element.setMime("image/jpeg");
			element.setCid(_storeWithString(access, thumb));
			elements.getElement().add(element);
		}
		if (null != video)
		{
			DataElement element = new DataElement();
			element.setMime("video/webm");
			element.setHeight(videoDimensions);
			element.setWidth(videoDimensions);
			element.setCid(_storeWithString(access, video));
			elements.getElement().add(element);
		}
		if (null != audio)
		{
			DataElement element = new DataElement();
			element.setMime("audio/ogg");
			element.setCid(_storeWithString(access, audio));
			elements.getElement().add(element);
		}
		
		StreamRecord streamRecord = new StreamRecord();
		streamRecord.setName(title);
		streamRecord.setDescription("desc");
		streamRecord.setPublisherKey(publisherKey.toPublicKey());
		streamRecord.setPublishedSecondsUtc(1L);
		streamRecord.setElements(elements);
		return _storeData(access, GlobalData.serializeRecord(streamRecord));
	}

	private static String _storeWithString(MockWritingAccess access, byte[] data) throws Throwable
	{
		return _storeData(access, data).toSafeString();
	}

	private static IpfsFile _storeData(MockWritingAccess access, byte[] data) throws Throwable
	{
		return access.uploadAndPin(new ByteArrayInputStream(data));
	}

	private static int _countPins(MockWritingAccess access)
	{
		int count = 0;
		for (Integer i : access.pins.values())
		{
			count += i;
		}
		return count;
	}
}
