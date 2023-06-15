package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.projection.IFavouritesReading;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.FutureSizedRead;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;


/**
 * An implementation of IWritingAccess for unit tests.
 */
public class MockWritingAccess implements IWritingAccess
{
	public final PrefsData prefsData = PrefsData.defaultPrefs();
	public final ExplicitCacheData explicitCacheData = new ExplicitCacheData();
	public final Map<IpfsFile, byte[]> data = new HashMap<>();
	public final Map<IpfsFile, Integer> pins = new HashMap<>();
	// The root of THIS storage.
	public IpfsFile root = null;
	// The key and root are not for the owner of the storage, but for the followee being resolved.
	public IpfsKey oneKey = null;
	public IpfsFile oneRoot = null;
	// Accounting.
	public int writes = 0;
	public int sizeChecksPerformed = 0;
	public int sizeAndReadPerformed = 0;

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
		return this.prefsData;
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
	public <R> FutureSizedRead<R> loadNotCached(IpfsFile file, String context, long maxSizeInBytes, DataDeserializer<R> decoder)
	{
		FutureSizedRead<R> r = new FutureSizedRead<>();
		byte[] data = this.data.get(file);
		if (null != data)
		{
			if (data.length <= maxSizeInBytes)
			{
				try
				{
					r.success(decoder.apply(this.data.get(file)));
				}
				catch (FailedDeserializationException e)
				{
					r.failureInDecoding(e);
				}
			}
			else
			{
				r.failureInSizeCheck(new SizeConstraintException("read", data.length, maxSizeInBytes));
			}
		}
		else
		{
			r.failureInConnection(new IpfsConnectionException("size", file, null));
		}
		this.sizeAndReadPerformed += 1;
		return r;
	}

	@Override
	public IpfsFile getLastRootElement()
	{
		return this.root;
	}

	@Override
	public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
	{
		// We expect that this is only called with the user we are configured to resolve.
		Assert.assertEquals(this.oneKey, keyToResolve);
		FutureResolve resolve = new FutureResolve(keyToResolve);
		if (null != this.oneRoot)
		{
			resolve.success(this.oneRoot);
		}
		else
		{
			resolve.failure(new IpfsConnectionException("resolve", "no result", null));
		}
		return resolve;
	}

	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		FutureSize size = new FutureSize();
		byte[] data = this.data.get(cid);
		if (null != data)
		{
			size.success(data.length);
		}
		else
		{
			size.failure(new IpfsConnectionException("size", cid, null));
		}
		this.sizeChecksPerformed += 1;
		return size;
	}

	@Override
	public ConcurrentTransaction openConcurrentTransaction()
	{
		throw new RuntimeException("Not Called");
	}

	@Override
	public IFavouritesReading readableFavouritesCache()
	{
		throw new RuntimeException("Not Called");
	}

	@Override
	public List<IReadingAccess.HomeUserTuple> readHomeUserData()
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
		FuturePin pin = new FuturePin(cid);
		if (this.data.containsKey(cid))
		{
			int count = this.pins.containsKey(cid) ? this.pins.get(cid).intValue() : 0;
			this.pins.put(cid, count + 1);
			pin.success();
		}
		else
		{
			pin.failure(new IpfsConnectionException("pin", cid, null));
		}
		return pin;
	}

	@Override
	public void unpin(IpfsFile cid) throws IpfsConnectionException
	{
		if (this.pins.containsKey(cid))
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

	@Override
	public ExplicitCacheData writableExplicitCache()
	{
		return this.explicitCacheData;
	}

	@Override
	public void deleteChannelData()
	{
		throw new RuntimeException("Not Called");
	}

	@Override
	public FavouritesCacheData writableFavouritesCache()
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

