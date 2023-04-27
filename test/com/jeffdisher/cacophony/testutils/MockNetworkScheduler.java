package com.jeffdisher.cacophony.testutils;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FutureKey;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.scheduler.FutureSave;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.FutureSizedRead;
import com.jeffdisher.cacophony.scheduler.FutureUnpin;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * An implementation of INetworkScheduler which provides very basic data/size look-ups and tracks requested pins.
 */
public class MockNetworkScheduler implements INetworkScheduler
{
	private final Map<IpfsFile, byte[]> _data = new HashMap<>();
	private final Set<IpfsFile> _addedPins = new HashSet<>();

	public IpfsFile storeData(byte[] data)
	{
		IpfsFile key = MockSingleNode.generateHash(data);
		// This might over-write this element but that is ok.
		_data.put(key, data);
		return key;
	}

	public int addedPinCount()
	{
		return _addedPins.size();
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
		// Not called in tests.
		throw Assert.unreachable();
	}

	@Override
	public FutureSave saveStream(InputStream stream)
	{
		// Not called in tests.
		throw Assert.unreachable();
	}

	@Override
	public FuturePublish publishIndex(String keyName, IpfsKey publicKey, IpfsFile indexHash)
	{
		// Not called in tests.
		throw Assert.unreachable();
	}

	@Override
	public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
	{
		// Not called in tests.
		throw Assert.unreachable();
	}

	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		byte[] data = _data.get(cid);
		Assert.assertTrue(null != data);
		FutureSize size = new FutureSize();
		size.success(data.length);
		return size;
	}

	@Override
	public FuturePin pin(IpfsFile cid)
	{
		Assert.assertTrue(!_addedPins.contains(cid));
		_addedPins.add(cid);
		FuturePin pin = new FuturePin(cid);
		pin.success();
		return pin;
	}

	@Override
	public FutureUnpin unpin(IpfsFile cid)
	{
		Assert.assertTrue(_addedPins.contains(cid));
		_addedPins.remove(cid);
		FutureUnpin unpin = new FutureUnpin();
		unpin.success();
		return unpin;
	}

	@Override
	public FutureKey getOrCreatePublicKey(String keyName)
	{
		// Not called in tests.
		throw Assert.unreachable();
	}
}
