package com.jeffdisher.cacophony.testutils;

import java.io.ByteArrayInputStream;

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
import com.jeffdisher.cacophony.logic.ExplicitCacheManager;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A collection of helper utilities for high-level interaction with MockSingleNode.
 */
public class MockNodeHelpers
{
	public static Context createWallClockContext(MockSingleNode connection, INetworkScheduler network)
	{
		LocalDataModel model;
		try
		{
			model = LocalDataModel.verifiedAndLoadedModel(LocalDataModel.NONE, new MemoryConfigFileSystem(null), network);
		}
		catch (UsageException e)
		{
			throw Assert.unexpected(e);
		}
		Context context = new Context(null
				, new Context.AccessTuple(model, connection, network)
				, () -> System.currentTimeMillis()
				, null
				, null
				, null
				, null
				, null
				, null
				, null
		);
		// WARNING:  This is not shut down so it MUST be synchronous.
		context.setExplicitCache(new ExplicitCacheManager(context, false));
		return context;
	}

	public static IpfsFile createAndPublishEmptyChannelWithDescription(MockSingleNode node, IpfsKey publishKey, String name, byte[] userPic) throws IpfsConnectionException
	{
		StreamDescription desc = new StreamDescription();
		desc.setName(name);
		desc.setDescription("description");
		desc.setPicture(_storeWithString(node, userPic));
		StreamRecords records = new StreamRecords();
		StreamRecommendations recom = new StreamRecommendations();
		StreamIndex index = new StreamIndex();
		index.setVersion(1);
		IpfsFile root;
		try
		{
			index.setDescription(_storeWithString(node, GlobalData.serializeDescription(desc)));
			index.setRecords(_storeWithString(node, GlobalData.serializeRecords(records)));
			index.setRecommendations(_storeWithString(node, GlobalData.serializeRecommendations(recom)));
			root = MockNodeHelpers.storeData(node, GlobalData.serializeIndex(index));
		}
		catch (SizeConstraintException e)
		{
			throw Assert.unexpected(e);
		}
		
		node.addNewKey(publishKey.toPublicKey(), publishKey);
		node.publish(publishKey.toPublicKey(), publishKey, root);
		return root;
	}

	public static IpfsFile storeStreamRecord(MockSingleNode node, IpfsKey publisherKey, String title, byte[] thumb, byte[] video, int videoDimensions, byte[] audio)
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
		try
		{
			return _storeData(node, GlobalData.serializeRecord(streamRecord));
		}
		catch (SizeConstraintException e)
		{
			throw Assert.unexpected(e);
		}
	}

	public static IpfsFile attachPostToUserAndPublish(MockSingleNode node, IpfsKey publishKey, IpfsFile root, IpfsFile post) throws IpfsConnectionException
	{
		IpfsFile newRoot;
		try
		{
			StreamIndex index = GlobalData.deserializeIndex(node.loadData(root));
			StreamRecords records = GlobalData.deserializeRecords(node.loadData(IpfsFile.fromIpfsCid(index.getRecords())));
			records.getRecord().add(post.toSafeString());
			IpfsFile newRecords = _storeData(node, GlobalData.serializeRecords(records));
			index.setRecords(newRecords.toSafeString());
			newRoot = _storeData(node, GlobalData.serializeIndex(index));
		}
		catch (FailedDeserializationException | IpfsConnectionException | SizeConstraintException e)
		{
			throw Assert.unexpected(e);
		}
		
		node.publish(publishKey.toPublicKey(), publishKey, root);
		return newRoot;
	}

	public static IpfsFile storeData(MockSingleNode node, byte[] data)
	{
		return _storeData(node, data);
	}


	private static String _storeWithString(MockSingleNode node, byte[] data)
	{
		return _storeData(node, data).toSafeString();
	}

	private static IpfsFile _storeData(MockSingleNode node, byte[] data)
	{
		return node.storeData(new ByteArrayInputStream(data));
	}

}
