package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.SizeLimits;
import com.jeffdisher.cacophony.utils.SizeLimits2;


public class TestForeignChannelReader
{
	@Test
	public void validCachedData() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		IpfsFile index = _buildEmptyUser(new CachedReader(access));
		ForeignChannelReader reader = new ForeignChannelReader(access, index, true);
		Assert.assertNotNull(reader.loadIndex());
		Assert.assertNotNull(reader.loadDescription());
		Assert.assertNotNull(reader.loadRecommendations());
		Assert.assertNotNull(reader.loadRecords());
	}

	@Test
	public void validNonCachedData() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		IpfsFile index = _buildEmptyUser(new NonCachedReader(access));
		ForeignChannelReader reader = new ForeignChannelReader(access, index, false);
		Assert.assertNotNull(reader.loadIndex());
		Assert.assertNotNull(reader.loadDescription());
		Assert.assertNotNull(reader.loadRecommendations());
		Assert.assertNotNull(reader.loadRecords());
	}

	@Test
	public void sizeLimit_StreamIndex() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		byte[] raw = new byte[(int)SizeLimits.MAX_INDEX_SIZE_BYTES + 1];
		IpfsFile index = access.storeWithoutPin(raw);
		ForeignChannelReader reader = new ForeignChannelReader(access, index, false);
		boolean didFail = false;
		try
		{
			Assert.assertNotNull(reader.loadIndex());
		}
		catch (SizeConstraintException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
	}

	@Test
	public void sizeLimit_StreamRecommendations() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		NonCachedReader store = new NonCachedReader(access);
		byte[] raw = new byte[(int)SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES + 1];
		IpfsFile recommendationsHash = store.apply(raw);
		
		IpfsFile descriptionHash = _store_StreamDescription(store);
		
		IpfsFile recordsHash = _store_StreamRecords(store);
		
		IpfsFile index = _store_StreamIndex(store, recommendationsHash, descriptionHash, recordsHash);
		ForeignChannelReader reader = new ForeignChannelReader(access, index, false);
		Assert.assertNotNull(reader.loadIndex());
		Assert.assertNotNull(reader.loadDescription());
		boolean didFail = false;
		try
		{
			Assert.assertNotNull(reader.loadRecommendations());
		}
		catch (SizeConstraintException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		Assert.assertNotNull(reader.loadRecords());
	}

	@Test
	public void testSizeLimit_StreamDescription() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		NonCachedReader store = new NonCachedReader(access);
		IpfsFile recommendationsHash = _store_StreamRecommendations(store);
		
		byte[] raw = new byte[(int)SizeLimits.MAX_DESCRIPTION_SIZE_BYTES + 1];
		IpfsFile descriptionHash = store.apply(raw);
		
		IpfsFile recordsHash = _store_StreamRecords(store);
		
		IpfsFile index = _store_StreamIndex(store, recommendationsHash, descriptionHash, recordsHash);
		ForeignChannelReader reader = new ForeignChannelReader(access, index, false);
		Assert.assertNotNull(reader.loadIndex());
		boolean didFail = false;
		try
		{
			Assert.assertNotNull(reader.loadDescription());
		}
		catch (SizeConstraintException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		Assert.assertNotNull(reader.loadRecommendations());
		Assert.assertNotNull(reader.loadRecords());
	}

	@Test
	public void testSizeLimit_StreamRecords() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		NonCachedReader store = new NonCachedReader(access);
		IpfsFile recommendationsHash = _store_StreamRecommendations(store);
		
		IpfsFile descriptionHash = _store_StreamDescription(store);
		
		byte[] raw = new byte[(int)SizeLimits2.MAX_RECORDS_SIZE_BYTES + 1];
		IpfsFile recordsHash = store.apply(raw);
		
		IpfsFile index = _store_StreamIndex(store, recommendationsHash, descriptionHash, recordsHash);
		ForeignChannelReader reader = new ForeignChannelReader(access, index, false);
		Assert.assertNotNull(reader.loadIndex());
		Assert.assertNotNull(reader.loadDescription());
		Assert.assertNotNull(reader.loadRecommendations());
		boolean didFail = false;
		try
		{
			Assert.assertNotNull(reader.loadRecords());
		}
		catch (SizeConstraintException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
	}

	@Test
	public void badVersion() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		StreamIndex index = new StreamIndex();
		index.setVersion(2);
		index.setDescription(MockSingleNode.generateHash(new byte[] { 1 }).toSafeString());
		index.setRecommendations(MockSingleNode.generateHash(new byte[] { 2 }).toSafeString());
		index.setRecords(MockSingleNode.generateHash(new byte[] { 3 }).toSafeString());
		IpfsFile indexRoot = access.storeWithoutPin(GlobalData.serializeIndex(index));
		ForeignChannelReader reader = new ForeignChannelReader(access, indexRoot, false);
		boolean didFail = false;
		try
		{
			Assert.assertNotNull(reader.loadIndex());
		}
		catch (FailedDeserializationException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
	}


	private static IpfsFile _buildEmptyUser(Function<byte[], IpfsFile> storeHelper) throws SizeConstraintException
	{
		// We build a fake user.
		IpfsFile recommendationsHash = _store_StreamRecommendations(storeHelper);
		
		IpfsFile descriptionHash = _store_StreamDescription(storeHelper);
		
		IpfsFile recordsHash = _store_StreamRecords(storeHelper);
		
		return _store_StreamIndex(storeHelper, recommendationsHash, descriptionHash, recordsHash);
	}

	private static IpfsFile _store_StreamRecommendations(Function<byte[], IpfsFile> storeHelper) throws SizeConstraintException
	{
		StreamRecommendations recommendations = new StreamRecommendations();
		byte[] serialized = GlobalData.serializeRecommendations(recommendations);
		return storeHelper.apply(serialized);
	}

	private static IpfsFile _store_StreamDescription(Function<byte[], IpfsFile> storeHelper) throws SizeConstraintException
	{
		byte[] userPic = new byte[] {'a','b','c'};
		IpfsFile userPicFile = storeHelper.apply(userPic);
		
		StreamDescription description = new StreamDescription();
		description.setName("name");
		description.setDescription("description");
		description.setPicture(userPicFile.toSafeString());
		byte[] serialized = GlobalData.serializeDescription(description);
		return storeHelper.apply(serialized);
	}

	private static IpfsFile _store_StreamRecords(Function<byte[], IpfsFile> storeHelper) throws SizeConstraintException
	{
		StreamRecords records = new StreamRecords();
		byte[] serialized = GlobalData.serializeRecords(records);
		return storeHelper.apply(serialized);
	}

	private static IpfsFile _store_StreamIndex(Function<byte[], IpfsFile> storeHelper, IpfsFile recommendationsHash, IpfsFile descriptionHash, IpfsFile recordsHash) throws SizeConstraintException
	{
		StreamIndex index = new StreamIndex();
		index.setVersion(1);
		index.setDescription(descriptionHash.toSafeString());
		index.setRecommendations(recommendationsHash.toSafeString());
		index.setRecords(recordsHash.toSafeString());
		byte[] serialized = GlobalData.serializeIndex(index);
		return storeHelper.apply(serialized);
	}


	private static class CachedReader implements Function<byte[], IpfsFile>
	{
		private final MockWritingAccess _access;
		
		public CachedReader(MockWritingAccess access)
		{
			_access = access;
		}
		
		@Override
		public IpfsFile apply(byte[] bytes)
		{
			try
			{
				return _access.uploadAndPin(new ByteArrayInputStream(bytes));
			}
			catch (IpfsConnectionException e)
			{
				throw new AssertionError(e);
			}
		}
	}

	private static class NonCachedReader implements Function<byte[], IpfsFile>
	{
		private final MockWritingAccess _access;
		
		public NonCachedReader(MockWritingAccess access)
		{
			_access = access;
		}
		
		@Override
		public IpfsFile apply(byte[] bytes)
		{
			return _access.storeWithoutPin(bytes);
		}
	}
}
