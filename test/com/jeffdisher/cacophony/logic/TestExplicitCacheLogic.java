package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.data.LocalDataModel;
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
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;


public class TestExplicitCacheLogic
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	@Test
	public void userNotFound() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = _createContext(node, scheduler);
		
		int startPin = node.pinCalls;
		boolean didFail;
		try
		{
			ExplicitCacheLogic.loadUserInfo(context, MockKeys.K1);
			didFail = false;
		}
		catch (KeyException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		int endPin = node.pinCalls;
		Assert.assertEquals(startPin, endPin);
		// We failed to resolve so we shouldn't read anything.
		Assert.assertEquals(0, node.sizeCalls);
		Assert.assertEquals(0, node.loadCalls);
		scheduler.shutdown();
	}

	@Test
	public void foundUser() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		_populateWithEmpty(upstream, MockKeys.K1, "userPic".getBytes());
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = _createContext(node, scheduler);
		
		ExplicitCacheData.UserInfo userInfo = ExplicitCacheLogic.loadUserInfo(context, MockKeys.K1);
		Assert.assertNotNull(userInfo);
		// While we pin all for elements (index, recommendations, description, picture), we don't actually load the picture.
		Assert.assertEquals(3, node.loadCalls);
		// We see size checks come from 2 different locations:
		// ForeignChannelReader: (3) index, description, recommendations
		// ExplicitCacheLogic: (4) userpic, index, recommendations, description
		Assert.assertEquals(7, node.sizeCalls);
		scheduler.shutdown();
	}

	@Test
	public void missingUserPic() throws Throwable
	{
		// Make sure that missing data causes this to fail and leave the pin counts unchanged.
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		byte[] userPic = "userPic".getBytes();
		_populateWithEmpty(upstream, MockKeys.K1, userPic);
		upstream.rm(MockSingleNode.generateHash(userPic));
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = _createContext(node, scheduler);
		
		int startPin = node.pinCalls;
		boolean didFail;
		try
		{
			ExplicitCacheLogic.loadUserInfo(context, MockKeys.K1);
			didFail = false;
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		int endPin = node.pinCalls;
		Assert.assertEquals(startPin, endPin);
		// While we pin all for elements (index, recommendations, description, picture), we don't actually load the picture.
		Assert.assertEquals(3, node.loadCalls);
		// We see size checks come from 2 different locations:
		// ForeignChannelReader: (3) index, description, recommendations
		// ExplicitCacheLogic: (1) userpic (we fail before getting on to the other elements)
		Assert.assertEquals(4, node.sizeCalls);
		scheduler.shutdown();
	}

	@Test
	public void missingRecommendations() throws Throwable
	{
		// Make sure that missing data causes this to fail and leave the pin counts unchanged.
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		_populateWithEmpty(upstream, MockKeys.K1, "userPic".getBytes());
		upstream.rm(MockSingleNode.generateHash(GlobalData.serializeRecommendations(new StreamRecommendations())));
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = _createContext(node, scheduler);
		
		int startPin = node.pinCalls;
		boolean didFail;
		try
		{
			ExplicitCacheLogic.loadUserInfo(context, MockKeys.K1);
			didFail = false;
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		int endPin = node.pinCalls;
		Assert.assertEquals(startPin, endPin);
		// We see 2 load attempts:  index, description.  Then, we fail on the recommendations size check.
		Assert.assertEquals(2, node.loadCalls);
		// ForeignChannelReader: (3) index, description, recommendations
		Assert.assertEquals(3, node.sizeCalls);
		scheduler.shutdown();
	}

	@Test
	public void recordNotFound() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = _createContext(node, scheduler);
		
		int startPin = node.pinCalls;
		boolean didFail;
		try
		{
			ExplicitCacheLogic.loadRecordInfo(context, MockSingleNode.generateHash(new byte[] {1}));
			didFail = false;
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		int endPin = node.pinCalls;
		Assert.assertEquals(startPin, endPin);
		// We directly check the record and see the failure in the size call, before the load call.
		Assert.assertEquals(0, node.loadCalls);
		Assert.assertEquals(1, node.sizeCalls);
		scheduler.shutdown();
	}

	@Test
	public void noLeafRecord() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		IpfsFile cid = _populateStreamRecord(upstream, MockKeys.K1, "name", null, null, 0, null);
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = _createContext(node, scheduler);
		
		CachedRecordInfo record = ExplicitCacheLogic.loadRecordInfo(context, cid);
		Assert.assertNotNull(record);
		// We check the size of the record before load and then when building total.
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(2, node.sizeCalls);
		// A second attempt should be a cache hit and not touch the network.
		CachedRecordInfo record2 = ExplicitCacheLogic.loadRecordInfo(context, cid);
		Assert.assertNotNull(record2);
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(2, node.sizeCalls);
		scheduler.shutdown();
	}

	@Test
	public void videoRecord() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		IpfsFile cid = _populateStreamRecord(upstream, MockKeys.K1, "name", "thumb".getBytes(), "video".getBytes(), 10, null);
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = _createContext(node, scheduler);
		
		CachedRecordInfo record = ExplicitCacheLogic.loadRecordInfo(context, cid);
		Assert.assertNotNull(record);
		// We check the record size, load it, then total record, thumbnail, and video.
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(4, node.sizeCalls);
		// A second attempt should be a cache hit and not touch the network.
		CachedRecordInfo record2 = ExplicitCacheLogic.loadRecordInfo(context, cid);
		Assert.assertNotNull(record2);
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(4, node.sizeCalls);
		scheduler.shutdown();
	}

	@Test
	public void audioRecord() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		IpfsFile cid = _populateStreamRecord(upstream, MockKeys.K1, "name", null, null, 0, "audio".getBytes());
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = _createContext(node, scheduler);
		
		CachedRecordInfo record = ExplicitCacheLogic.loadRecordInfo(context, cid);
		Assert.assertNotNull(record);
		// We check the record size, load it, then total record and audio.
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(3, node.sizeCalls);
		// A second attempt should be a cache hit and not touch the network.
		CachedRecordInfo record2 = ExplicitCacheLogic.loadRecordInfo(context, cid);
		Assert.assertNotNull(record2);
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(3, node.sizeCalls);
		scheduler.shutdown();
	}

	@Test
	public void brokenRecordLeaf() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		// Break the video leaf and make sure we fail and don't change pin counts.
		byte[] videoData = "video".getBytes();
		IpfsFile cid = _populateStreamRecord(upstream, MockKeys.K1, "name", "thumb".getBytes(), videoData, 10, null);
		upstream.rm(MockSingleNode.generateHash(videoData));
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = _createContext(node, scheduler);
		
		int startPin = node.getStoredFileSet().size();
		boolean didFail;
		try
		{
			ExplicitCacheLogic.loadRecordInfo(context, cid);
			didFail = false;
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		int endPin = node.getStoredFileSet().size();
		Assert.assertEquals(startPin, endPin);
		// We should see that pin calls were made, but were reverted.
		Assert.assertEquals(3, node.pinCalls);
		// We check the record size, load it, then total record, thumb, and the video pin fails before we check it.
		Assert.assertEquals(1, node.loadCalls);
		Assert.assertEquals(3, node.sizeCalls);
		scheduler.shutdown();
	}

	@Test
	public void existingRecord() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		IpfsFile cid = _populateStreamRecord(upstream, MockKeys.K1, "name", null, null, 0, null);
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = _createContext(node, scheduler);
		
		Assert.assertNull(ExplicitCacheLogic.getExistingRecordInfo(context, cid));
		CachedRecordInfo record = ExplicitCacheLogic.loadRecordInfo(context, cid);
		Assert.assertNotNull(record);
		Assert.assertTrue(record == ExplicitCacheLogic.getExistingRecordInfo(context, cid));
		scheduler.shutdown();
	}

	@Test
	public void concurrentUser() throws Throwable
	{
		// We want to start multiple threads, operating on the same data store (acting as concurrent requests on the
		// same running server) and have them all perform the same request, verifying that the final state has the same
		// state cached on the local node.
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		_populateWithEmpty(upstream, MockKeys.K1, "userPic".getBytes());
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = _createContext(node, scheduler);
		
		Thread[] threads = new Thread[10];
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i] = new Thread(() -> {
				ExplicitCacheData.UserInfo userInfo;
				try
				{
					userInfo = ExplicitCacheLogic.loadUserInfo(context, MockKeys.K1);
				}
				catch (ProtocolDataException e)
				{
					// Not expected.
					throw new AssertionError(e);
				}
				catch (IpfsConnectionException e)
				{
					// Not expected.
					throw new AssertionError(e);
				}
				catch (KeyException e)
				{
					// Not expected.
					throw new AssertionError(e);
				}
				Assert.assertNotNull(userInfo);
			});
		}
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i].start();
		}
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i].join();
		}
		// While we pin all 4 elements (index, recommendations, description, picture), we don't actually load the picture.
		Assert.assertEquals(4, node.getStoredFileSet().size());
		
		// This test is somewhat racy so we know that we will see between 1 and 10 load attempts (usually 10), but we expect each attempt to have a consistent multiple.
		Assert.assertTrue(node.loadCalls >= 3);
		Assert.assertTrue(node.loadCalls <= (3 * threads.length));
		// We see size checks come from 2 different locations:
		// ForeignChannelReader: (3) index, description, recommendations
		// ExplicitCacheLogic: (4) userpic, index, recommendations, description
		Assert.assertTrue(node.sizeCalls >= 7);
		Assert.assertTrue(node.sizeCalls <= (7 * threads.length));
		int multiple = node.loadCalls / 3;
		Assert.assertEquals(7 * multiple, node.sizeCalls);
		scheduler.shutdown();
	}

	@Test
	public void concurrentRecord() throws Throwable
	{
		// We want to start multiple threads, operating on the same data store (acting as concurrent requests on the
		// same running server) and have them all perform the same request, verifying that the final state has the same
		// state cached on the local node.
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		MockSingleNode upstream = new MockSingleNode(swarm);
		IpfsFile cid = _populateStreamRecord(upstream, MockKeys.K1, "name", "thumb".getBytes(), "video".getBytes(), 10, null);
		
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		Context context = _createContext(node, scheduler);
		
		Thread[] threads = new Thread[10];
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i] = new Thread(() -> {
				CachedRecordInfo record;
				try
				{
					record = ExplicitCacheLogic.loadRecordInfo(context, cid);
				}
				catch (ProtocolDataException e)
				{
					// Not expected.
					throw new AssertionError(e);
				}
				catch (IpfsConnectionException e)
				{
					// Not expected.
					throw new AssertionError(e);
				}
				Assert.assertNotNull(record);
			});
		}
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i].start();
		}
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i].join();
		}
		// We check the record size, load it, then total record, thumbnail, and video.
		// We know that this results in 3 pins (the record, the thumbnail, and video):
		Assert.assertEquals(3, node.getStoredFileSet().size());
		// We should see up to 10x the usual count, since each thread does the same thing, even if only 1 writes-back.
		// (this will usually be 10x but can technically be as low as 1x, but we expect the right multiples).
		Assert.assertTrue(node.loadCalls >= 1);
		Assert.assertTrue(node.loadCalls <= threads.length);
		Assert.assertTrue(node.sizeCalls >= 4);
		Assert.assertTrue(node.sizeCalls <= (4 * threads.length));
		// We expect X * 1 for loads, so that is our multiple.
		Assert.assertEquals(node.sizeCalls, 4 * node.loadCalls);
		scheduler.shutdown();
	}


	private static void _populateWithEmpty(MockSingleNode node, IpfsKey publishKey, byte[] userPic) throws Throwable
	{
		StreamDescription desc = new StreamDescription();
		desc.setName("name");
		desc.setDescription("description");
		desc.setPicture(_storeWithString(node, userPic));
		StreamRecords records = new StreamRecords();
		StreamRecommendations recom = new StreamRecommendations();
		StreamIndex index = new StreamIndex();
		index.setVersion(1);
		index.setDescription(_storeWithString(node, GlobalData.serializeDescription(desc)));
		index.setRecords(_storeWithString(node, GlobalData.serializeRecords(records)));
		index.setRecommendations(_storeWithString(node, GlobalData.serializeRecommendations(recom)));
		IpfsFile root = _storeData(node, GlobalData.serializeIndex(index));
		
		node.addNewKey("key", publishKey);
		node.publish("key", publishKey, root);
	}

	private static IpfsFile _populateStreamRecord(MockSingleNode node, IpfsKey publisherKey, String title, byte[] thumb, byte[] video, int videoDimensions, byte[] audio) throws Throwable
	{
		DataArray elements = new DataArray();
		if (null != thumb)
		{
			DataElement element = new DataElement();
			element.setSpecial(ElementSpecialType.IMAGE);
			element.setMime("image/jpeg");
			element.setCid(_storeWithString(node, thumb));
			elements.getElement().add(element);
		}
		if (null != video)
		{
			DataElement element = new DataElement();
			element.setMime("video/webm");
			element.setHeight(videoDimensions);
			element.setWidth(videoDimensions);
			element.setCid(_storeWithString(node, video));
			elements.getElement().add(element);
		}
		if (null != audio)
		{
			DataElement element = new DataElement();
			element.setMime("audio/ogg");
			element.setCid(_storeWithString(node, audio));
			elements.getElement().add(element);
		}
		
		StreamRecord streamRecord = new StreamRecord();
		streamRecord.setName(title);
		streamRecord.setDescription("desc");
		streamRecord.setPublisherKey(publisherKey.toPublicKey());
		streamRecord.setPublishedSecondsUtc(1L);
		streamRecord.setElements(elements);
		return _storeData(node, GlobalData.serializeRecord(streamRecord));
	}

	private static String _storeWithString(MockSingleNode node, byte[] data) throws Throwable
	{
		return _storeData(node, data).toSafeString();
	}

	private static IpfsFile _storeData(MockSingleNode node, byte[] data) throws Throwable
	{
		return node.storeData(new ByteArrayInputStream(data));
	}

	private static Context _createContext(IConnection connection, INetworkScheduler network) throws UsageException
	{
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(LocalDataModel.NONE, new MemoryConfigFileSystem(null), network);
		Context context = new Context(null
				, model
				, connection
				, network
				, () -> System.currentTimeMillis()
				, null
				, null
				, null
				, null
				, null
				, null
				, null
		);
		return context;
	}
}
