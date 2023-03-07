package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;


public class TestChannelModifier
{
	@Test
	public void testEmpty() throws Throwable
	{
		Access access = new Access();
		_populateWithEmpty(access);
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(4, access.writes);
	}

	@Test
	public void testReadWriteEmpty() throws Throwable
	{
		Access access = new Access();
		_populateWithEmpty(access);
		access.writes = 0;
		ChannelModifier modifier = new ChannelModifier(access);
		StreamDescription desc = modifier.loadDescription();
		StreamRecords records = modifier.loadRecords();
		StreamRecommendations recom = modifier.loadRecommendations();
		Assert.assertNotNull(desc);
		Assert.assertNotNull(records);
		Assert.assertNotNull(recom);
		IpfsFile initialRoot = access.root;
		IpfsFile updated = modifier.commitNewRoot();
		Assert.assertEquals(initialRoot, updated);
		Assert.assertEquals(initialRoot, access.root);
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(0, access.writes);
	}

	@Test
	public void testUpdateDescription() throws Throwable
	{
		Access access = new Access();
		_populateWithEmpty(access);
		access.writes = 0;
		ChannelModifier modifier = new ChannelModifier(access);
		StreamDescription desc = modifier.loadDescription();
		desc.setName("updated name");
		modifier.storeDescription(desc);
		IpfsFile root = modifier.commitNewRoot();
		Assert.assertEquals(access.root, root);
		modifier = new ChannelModifier(access);
		desc = modifier.loadDescription();
		Assert.assertEquals("updated name", desc.getName());
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(2, access.writes);
	}

	@Test
	public void testUpdateRecords() throws Throwable
	{
		Access access = new Access();
		_populateWithEmpty(access);
		access.writes = 0;
		ChannelModifier modifier = new ChannelModifier(access);
		StreamRecords records = modifier.loadRecords();
		records.getRecord().add(MockSingleNode.generateHash("fake post".getBytes()).toSafeString());
		modifier.storeRecords(records);
		IpfsFile root = modifier.commitNewRoot();
		Assert.assertEquals(access.root, root);
		modifier = new ChannelModifier(access);
		records = modifier.loadRecords();
		Assert.assertEquals(1, records.getRecord().size());
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(2, access.writes);
	}

	@Test
	public void testUpdateRecommendations() throws Throwable
	{
		Access access = new Access();
		_populateWithEmpty(access);
		access.writes = 0;
		ChannelModifier modifier = new ChannelModifier(access);
		StreamRecommendations recom = modifier.loadRecommendations();
		recom.getUser().add("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
		modifier.storeRecommendations(recom);
		IpfsFile root = modifier.commitNewRoot();
		Assert.assertEquals(access.root, root);
		modifier = new ChannelModifier(access);
		recom = modifier.loadRecommendations();
		Assert.assertEquals(1, recom.getUser().size());
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(2, access.writes);
	}

	@Test
	public void testEmptyUpdate() throws Throwable
	{
		Access access = new Access();
		_populateWithEmpty(access);
		access.writes = 0;
		ChannelModifier modifier = new ChannelModifier(access);
		// We define it as invalid to commit if nothing has been read so check for the assertion error.
		boolean didFail = false;
		try
		{
			modifier.commitNewRoot();
			Assert.fail();
		}
		catch (AssertionError e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
	}

	@Test
	public void testVacuousUpdate() throws Throwable
	{
		Access access = new Access();
		_populateWithEmpty(access);
		access.writes = 0;
		ChannelModifier modifier = new ChannelModifier(access);
		modifier.storeDescription(modifier.loadDescription());
		modifier.storeRecords(modifier.loadRecords());
		modifier.storeRecommendations(modifier.loadRecommendations());
		IpfsFile initialRoot = access.root;
		IpfsFile updated = modifier.commitNewRoot();
		Assert.assertEquals(initialRoot, updated);
		Assert.assertEquals(initialRoot, access.root);
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(4, access.writes);
	}


	private static void _populateWithEmpty(Access access) throws Throwable
	{
		StreamDescription desc = new StreamDescription();
		desc.setName("name");
		desc.setDescription("description");
		desc.setPicture(MockSingleNode.generateHash("fake picture cid source".getBytes()).toSafeString());
		StreamRecords records = new StreamRecords();
		StreamRecommendations recom = new StreamRecommendations();
		StreamIndex index = new StreamIndex();
		index.setDescription(_storeWithString(access, GlobalData.serializeDescription(desc)));
		index.setRecords(_storeWithString(access, GlobalData.serializeRecords(records)));
		index.setRecommendations(_storeWithString(access, GlobalData.serializeRecommendations(recom)));
		access.uploadIndexAndUpdateTracking(index);
	}

	private static String _storeWithString(Access access, byte[] data) throws Throwable
	{
		return access.uploadAndPin(new ByteArrayInputStream(data)).toSafeString();
	}

	private static int _countPins(Access access)
	{
		int count = 0;
		for (Integer i : access.pins.values())
		{
			count += i;
		}
		return count;
	}


	private static final class Access implements IWritingAccess
	{
		public final Map<IpfsFile, byte[]> data = new HashMap<>();
		public final Map<IpfsFile, Integer> pins = new HashMap<>();
		public IpfsFile root = null;
		public int writes = 0;
		@Override
		public void close()
		{
		}
		@Override
		public IFolloweeReading readableFolloweeData()
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public boolean isInPinCached(IpfsFile file)
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public PrefsData readPrefs()
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public void requestIpfsGc() throws IpfsConnectionException
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public <R> FutureRead<R> loadCached(IpfsFile file, DataDeserializer<R> decoder)
		{
			FutureRead<R> r = new FutureRead<>();
			try
			{
				r.success(decoder.apply(this.data.get(file)));
			}
			catch (FailedDeserializationException e)
			{
				Assert.fail();
			}
			return r;
		}
		@Override
		public <R> FutureRead<R> loadNotCached(IpfsFile file, DataDeserializer<R> decoder)
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public URL getCachedUrl(IpfsFile file)
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public IpfsFile getLastRootElement()
		{
			return this.root;
		}
		@Override
		public IpfsKey getPublicKey()
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public FutureSize getSizeInBytes(IpfsFile cid)
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public FuturePublish republishIndex()
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public ConcurrentTransaction openConcurrentTransaction()
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public String getDirectFetchUrlRoot()
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public IFolloweeWriting writableFolloweeData()
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public void writePrefs(PrefsData prefs)
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public IpfsFile uploadAndPin(InputStream dataToSave) throws IpfsConnectionException
		{
			byte[] data = null;
			try
			{
				data = dataToSave.readAllBytes();
				dataToSave.close();
			}
			catch (IOException e)
			{
				Assert.fail();
			}
			IpfsFile file = MockSingleNode.generateHash(data);
			this.data.put(file, data);
			int count = this.pins.containsKey(file) ? this.pins.get(file).intValue() : 0;
			this.pins.put(file, count + 1);
			this.writes += 1;
			return file;
		}
		@Override
		public IpfsFile uploadIndexAndUpdateTracking(StreamIndex streamIndex) throws IpfsConnectionException
		{
			byte[] data;
			try
			{
				data = GlobalData.serializeIndex(streamIndex);
			}
			catch (SizeConstraintException e)
			{
				// We created this as well-formed so it can't be this large.
				throw new AssertionError(e);
			}
			IpfsFile file = MockSingleNode.generateHash(data);
			this.data.put(file, data);
			int count = this.pins.containsKey(file) ? this.pins.get(file).intValue() : 0;
			this.pins.put(file, count + 1);
			this.writes += 1;
			this.root = file;
			return file;
		}
		@Override
		public FuturePin pin(IpfsFile cid)
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public void unpin(IpfsFile cid) throws IpfsConnectionException
		{
			int count = this.pins.get(cid).intValue();
			if (1 == count)
			{
				this.pins.remove(cid);
				this.data.remove(cid);
			}
			else
			{
				this.pins.put(cid, count - 1);
			}
		}
		@Override
		public FuturePublish beginIndexPublish(IpfsFile indexRoot)
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public void commitTransactionPinCanges(Map<IpfsFile, Integer> changedPinCounts, Set<IpfsFile> falsePins)
		{
			throw new RuntimeException("Not Called");
		}
	}
}
