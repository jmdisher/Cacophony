package com.jeffdisher.cacophony.logic;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.scheduler.FutureSave;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.FutureSizedRead;
import com.jeffdisher.cacophony.scheduler.FutureUnpin;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;


public class TestPinCacheBuilder
{
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final IpfsFile F3 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC");
	private static final IpfsKey K1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testEmpty() throws Throwable
	{
		DataOnlyNetwork network = new DataOnlyNetwork();
		PinCacheBuilder builder = new PinCacheBuilder(network);
		PinCacheData data = builder.finish();
		Assert.assertTrue(data.snapshotPinnedSet().isEmpty());
	}

	@Test
	public void testHomeAndOneFollower() throws Throwable
	{
		DataOnlyNetwork network = new DataOnlyNetwork();
		
		// Create the home data with 1 post with one attachment.
		IpfsFile homePost = _makePost(network, "First post!", F2);
		IpfsFile homeRoot = _createUser(network, "Home", F1, homePost);
		
		// Create the followee data with 1 post with one attachment.
		IpfsFile followeePost = _makePost(network, "followee post", F3);
		IpfsFile followeeRoot = _createUser(network, "Followee", F1, followeePost);
		
		PinCacheBuilder builder = new PinCacheBuilder(network);
		builder.addHomeUser(homeRoot);
		builder.addFollowee(followeeRoot, Collections.singletonMap(followeePost, new FollowingCacheElement(followeePost, F3, null, 1L)));
		PinCacheData data = builder.finish();
		
		// We expect to see the following:
		// F1 (x2)
		// recommendations (x2)
		// F2
		// F3
		// home post
		// follow post
		// home description
		// follow description
		// home streams
		// follow streams
		// home index
		// follow index
		Assert.assertEquals(12, data.snapshotPinnedSet().size());
		
		// Verify the double-counts.
		data.delRef(F1);
		data.delRef(F1);
		
		IpfsFile emptyRecommendations = MockSingleNode.generateHash(GlobalData.serializeRecommendations(new StreamRecommendations()));
		data.delRef(emptyRecommendations);
		data.delRef(emptyRecommendations);
		Assert.assertEquals(10, data.snapshotPinnedSet().size());
	}


	private IpfsFile _createUser(DataOnlyNetwork network, String name, IpfsFile pic, IpfsFile post) throws SizeConstraintException
	{
		StreamDescription description = new StreamDescription();
		description.setName(name);
		description.setDescription("Description forthcoming");
		description.setPicture(pic.toSafeString());
		
		StreamRecommendations recommendations = new StreamRecommendations();
		
		StreamRecords records = new StreamRecords();
		records.getRecord().add(post.toSafeString());
		
		StreamIndex streamIndex = new StreamIndex();
		streamIndex.setVersion(1);
		streamIndex.setDescription(network.storeData(GlobalData.serializeDescription(description)).toSafeString());
		streamIndex.setRecommendations(network.storeData(GlobalData.serializeRecommendations(recommendations)).toSafeString());
		streamIndex.setRecords(network.storeData(GlobalData.serializeRecords(records)).toSafeString());
		
		return network.storeData(GlobalData.serializeIndex(streamIndex));
	}

	private IpfsFile _makePost(DataOnlyNetwork network, String title, IpfsFile image) throws SizeConstraintException
	{
		StreamRecord record = new StreamRecord();
		record.setName(title);
		record.setDescription("desc");
		record.setPublishedSecondsUtc(1L);
		record.setPublisherKey(K1.toPublicKey());
		DataArray elements = new DataArray();
		if (null != image)
		{
			DataElement element = new DataElement();
			element.setCid(image.toSafeString());
			element.setMime("image/jpeg");
			element.setSpecial(ElementSpecialType.IMAGE);
			elements.getElement().add(element);
		}
		record.setElements(elements);
		return network.storeData(GlobalData.serializeRecord(record));
	}


	private static class DataOnlyNetwork implements INetworkScheduler
	{
		private final Map<IpfsFile, byte[]> _data = new HashMap<>();
		public IpfsFile storeData(byte[] data)
		{
			IpfsFile key = MockSingleNode.generateHash(data);
			// This might over-write this element but that is ok.
			_data.put(key, data);
			return key;
		}
		@Override
		public <R> FutureRead<R> readData(IpfsFile file, DataDeserializer<R> decoder)
		{
			Assert.assertTrue(_data.containsKey(file));
			FutureRead<R> read = new FutureRead<>();
			try
			{
				read.success(decoder.apply(_data.get(file)));
			}
			catch (FailedDeserializationException e)
			{
				throw new AssertionError("This component only operates on already-valid data");
			}
			return read;
		}
		@Override
		public <R> FutureSizedRead<R> readDataWithSizeCheck(IpfsFile file, String context, long maxSizeInBytes, DataDeserializer<R> decoder)
		{
			throw new AssertionError("Not called");
		}
		@Override
		public FutureSave saveStream(InputStream stream)
		{
			throw new AssertionError("Not called");
		}
		@Override
		public FuturePublish publishIndex(String keyName, IpfsKey publicKey, IpfsFile indexHash)
		{
			throw new AssertionError("Not called");
		}
		@Override
		public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
		{
			throw new AssertionError("Not called");
		}
		@Override
		public FutureSize getSizeInBytes(IpfsFile cid)
		{
			throw new AssertionError("Not called");
		}
		@Override
		public FuturePin pin(IpfsFile cid)
		{
			throw new AssertionError("Not called");
		}
		@Override
		public FutureUnpin unpin(IpfsFile cid)
		{
			throw new AssertionError("Not called");
		}
	}
}
