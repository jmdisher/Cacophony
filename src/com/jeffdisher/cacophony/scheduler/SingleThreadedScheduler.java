package com.jeffdisher.cacophony.scheduler;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * An implementation of INetworkScheduler which runs all calls inline, before returning the future.
 * This is mostly just meant as a stop-gap while the core logic is cut-over to the future-based design.
 */
public class SingleThreadedScheduler implements INetworkScheduler
{
	private final RemoteActions _remote;

	public SingleThreadedScheduler(RemoteActions actions)
	{
		_remote = actions;
	}

	@Override
	public <R> FutureRead<R> readData(IpfsFile file, Function<byte[], R> decoder)
	{
		FutureRead<R> future = new FutureRead<R>();
		try
		{
			byte[] data = _remote.readData(file);
			future.success(decoder.apply(data));
		}
		catch (IpfsConnectionException e)
		{
			future.failure(e);
		}
		return future;
	}

	@Override
	public FutureSave saveStream(InputStream stream, boolean shouldCloseStream)
	{
		FutureSave future = new FutureSave();
		try
		{
			IpfsFile file = _remote.saveStream(stream);
			future.success(file);
		}
		catch (IpfsConnectionException e)
		{
			future.failure(e);
		}
		finally
		{
			if (shouldCloseStream)
			{
				try
				{
					stream.close();
				}
				catch (IOException e)
				{
					// We don't expect our streams to fail to close.
					throw Assert.unexpected(e);
				}
			}
		}
		return future;
	}

	@Override
	public FuturePublish publishIndex(IpfsFile indexHash)
	{
		FuturePublish future = new FuturePublish();
		IpfsConnectionException error = _remote.publishIndex(indexHash);
		if (null == error)
		{
			future.success();
		}
		else
		{
			future.failure(error);
		}
		return future;
	}

	@Override
	public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
	{
		FutureResolve future = new FutureResolve();
		IpfsFile resolved = _remote.resolvePublicKey(keyToResolve);
		if (null != resolved)
		{
			future.success(resolved);
		}
		else
		{
			future.failure();
		}
		return future;
	}

	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		FutureSize future = new FutureSize();
		try
		{
			long sizeInBytes = _remote.getSizeInBytes(cid);
			future.success(sizeInBytes);
		}
		catch (IpfsConnectionException e)
		{
			future.failure(e);
		}
		return future;
	}
}
