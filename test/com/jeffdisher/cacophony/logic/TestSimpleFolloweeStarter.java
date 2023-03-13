package com.jeffdisher.cacophony.logic;

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
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
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


public class TestSimpleFolloweeStarter
{
	private static final IpfsKey K1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final IpfsFile EXPECTED_ROOT = IpfsFile.fromIpfsCid("QmRuFGRb7LoJGrAWjmZUmjVATxgzdLGVW2muLeCFSLWzjZ");
	private static final IpfsFile EXPECTED_FAKE = IpfsFile.fromIpfsCid("QmR7Yp8rMWxBmVFidiV6CB4vRixxRfjiZcULtaCYnKAvtP");

	@Test
	public void testInitialSetup() throws Throwable
	{
		Access access = new Access();
		IpfsFile root = _populateWithOneElement(access);
		Assert.assertEquals(6, access.data.size());
		Assert.assertEquals(0, access.pins.values().size());
		Assert.assertEquals(EXPECTED_ROOT, root);
	}

	@Test
	public void testStart() throws Throwable
	{
		Access access = new Access();
		IpfsFile root = _populateWithOneElement(access);
		Assert.assertEquals(6, access.data.size());
		Assert.assertEquals(0, access.pins.values().size());
		Assert.assertEquals(EXPECTED_ROOT, root);
		
		access.oneKey = K1;
		access.oneRoot = root;
		IpfsFile file = SimpleFolloweeStarter.startFollowingWithEmptyRecords((String message) -> {}, access, K1);
		Assert.assertEquals(EXPECTED_FAKE, file);
		// We expect that an extra 2 elements were uploaded (the fake StreamRecords and the fake StreamIndex).
		Assert.assertEquals(8, access.data.size());
		// 3 of these are missing:  real StreamIndex, real StreamRecords, real StreamRecord.
		Assert.assertEquals(5, access.pins.values().size());
	}


	private static IpfsFile _populateWithOneElement(Access access) throws Throwable
	{
		String userPicCid = _storeWithString(access, "fake picture cid source".getBytes());
		StreamDescription desc = new StreamDescription();
		desc.setName("name");
		desc.setDescription("description");
		desc.setPicture(userPicCid);
		
		StreamRecord record = new StreamRecord();
		record.setName("post");
		record.setDescription("record description");
		record.setPublishedSecondsUtc(1L);
		record.setPublisherKey(K1.toPublicKey());
		record.setElements(new DataArray());
		
		StreamRecords records = new StreamRecords();
		records.getRecord().add(_storeWithString(access, GlobalData.serializeRecord(record)));
		
		StreamRecommendations recom = new StreamRecommendations();
		StreamIndex index = new StreamIndex();
		index.setDescription(_storeWithString(access, GlobalData.serializeDescription(desc)));
		index.setRecords(_storeWithString(access, GlobalData.serializeRecords(records)));
		index.setRecommendations(_storeWithString(access, GlobalData.serializeRecommendations(recom)));
		return access.storeWithoutPin(GlobalData.serializeIndex(index));
	}

	private static String _storeWithString(Access access, byte[] data) throws Throwable
	{
		IpfsFile file = access.storeWithoutPin(data);
		return file.toSafeString();
	}


	private static final class Access implements IWritingAccess
	{
		public final Map<IpfsFile, byte[]> data = new HashMap<>();
		public final Map<IpfsFile, Integer> pins = new HashMap<>();
		// The key and root are not for the owner of the storage, but for the followee being resolved.
		public IpfsKey oneKey = null;
		public IpfsFile oneRoot = null;
		
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
			// Cached loads MUST be pinned.
			Assert.assertTrue(pins.containsKey(file));
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
			// While non-cached loads _could_ be theoretically pinned (since the element can be found via multiple paths), this test doesn't do that.
			Assert.assertFalse(pins.containsKey(file));
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
		public URL getCachedUrl(IpfsFile file)
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public IpfsFile getLastRootElement()
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public IpfsKey getPublicKey()
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
		{
			Assert.assertEquals(this.oneKey, keyToResolve);
			FutureResolve resolve = new FutureResolve();
			resolve.success(this.oneRoot);
			return resolve;
		}
		@Override
		public FutureSize getSizeInBytes(IpfsFile cid)
		{
			Assert.assertTrue(this.data.containsKey(cid));
			FutureSize size = new FutureSize();
			size.success(this.data.get(cid).length);
			return size;
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
			// In this set of tests, we assume that the data is new.
			Assert.assertFalse(this.pins.containsKey(file));
			Assert.assertFalse(this.data.containsKey(file));
			
			this.data.put(file, data);
			this.pins.put(file, 1);
			return file;
		}
		@Override
		public IpfsFile uploadIndexAndUpdateTracking(StreamIndex streamIndex) throws IpfsConnectionException
		{
			throw new RuntimeException("Not Called");
		}
		@Override
		public FuturePin pin(IpfsFile cid)
		{
			// While multiple pins could happen in real usage, that doesn't happen in this test.
			Assert.assertTrue(this.data.containsKey(cid));
			Assert.assertFalse(this.pins.containsKey(cid));
			this.pins.put(cid, 1);
			FuturePin pin = new FuturePin(cid);
			pin.success();
			return pin;
		}
		@Override
		public void unpin(IpfsFile cid) throws IpfsConnectionException
		{
			Assert.assertTrue(this.data.containsKey(cid));
			Assert.assertTrue(this.pins.containsKey(cid));
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
		public IpfsFile storeWithoutPin(byte[] data)
		{
			IpfsFile file = MockSingleNode.generateHash(data);
			Assert.assertFalse(this.data.containsKey(file));
			this.data.put(file, data);
			return file;
		}
	}
}
