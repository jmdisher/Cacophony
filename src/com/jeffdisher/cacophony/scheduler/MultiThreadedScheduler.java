package com.jeffdisher.cacophony.scheduler;

import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.MiscHelpers;


/**
 * An implementation of INetworkScheduler which runs all calls on a set of background threads, via a work queue.
 * It relies on being explicitly shut down in order to stop running.
 */
public class MultiThreadedScheduler implements INetworkScheduler
{
	private final RemoteActions _remote;
	private final WorkQueue _queue;
	private final Thread[] _threads;

	public MultiThreadedScheduler(RemoteActions actions, int threadCount)
	{
		_remote = actions;
		_queue = new WorkQueue();
		_threads = new Thread[threadCount];
		for (int i = 0; i < threadCount; ++i)
		{
			_threads[i] = MiscHelpers.createThread(() -> {
				boolean keepRunning = true;
				while (keepRunning)
				{
					Runnable run = _queue.pollForNext();
					if (null != run)
					{
						run.run();
					}
					else
					{
						keepRunning = false;
					}
				}
			}, "Scheduler thread #" + i);
			_threads[i].start();
		}
	}

	@Override
	public <R> FutureRead<R> readData(IpfsFile file, DataDeserializer<R> decoder)
	{
		FutureRead<R> future = new FutureRead<R>();
		Runnable r = () -> {
			try
			{
				byte[] data = _remote.readData(file);
				future.success(decoder.apply(data));
			}
			catch (IpfsConnectionException e)
			{
				future.failureInConnection(e);
			}
			catch (FailedDeserializationException e)
			{
				future.failureInDecoding(e);
			}
		};
		_queue.enqueue(r);
		return future;
	}

	@Override
	public FutureSave saveStream(InputStream stream)
	{
		FutureSave future = new FutureSave();
		Runnable r = () -> {
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
		};
		_queue.enqueue(r);
		return future;
	}

	@Override
	public FuturePublish publishIndex(String keyName, IpfsKey publicKey, IpfsFile indexHash)
	{
		FuturePublish future = new FuturePublish(indexHash);
		Runnable r = () -> {
			try
			{
				_remote.publishIndex(keyName, publicKey, indexHash);
				future.success();
			}
			catch (IpfsConnectionException e)
			{
				future.failure(e);
			}
		};
		_queue.enqueue(r);
		return future;
	}

	@Override
	public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
	{
		Assert.assertTrue(null != keyToResolve);
		FutureResolve future = new FutureResolve();
		Runnable r = () -> {
			try
			{
				IpfsFile resolved = _remote.resolvePublicKey(keyToResolve);
				// This should only fail with an exception.
				Assert.assertTrue(null != resolved);
				future.success(resolved);
			}
			catch (IpfsConnectionException e)
			{
				future.failure();
			}
		};
		_queue.enqueue(r);
		return future;
	}

	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		FutureSize future = new FutureSize();
		Runnable r = () -> {
			try
			{
				long sizeInBytes = _remote.getSizeInBytes(cid);
				future.success(sizeInBytes);
			}
			catch (IpfsConnectionException e)
			{
				future.failure(e);
			}
		};
		_queue.enqueue(r);
		return future;
	}

	@Override
	public FuturePin pin(IpfsFile cid)
	{
		FuturePin future = new FuturePin(cid);
		Runnable r = () -> {
			try
			{
				_remote.pin(cid);
				future.success();
			}
			catch (IpfsConnectionException e)
			{
				future.failure(e);
			}
		};
		_queue.enqueue(r);
		return future;
	}

	@Override
	public FutureUnpin unpin(IpfsFile cid)
	{
		FutureUnpin future = new FutureUnpin();
		Runnable r = () -> {
			try
			{
				_remote.unpin(cid);
				future.success();
			}
			catch (IpfsConnectionException e)
			{
				future.failure(e);
			}
		};
		_queue.enqueue(r);
		return future;
	}

	@Override
	public void shutdown()
	{
		_queue.shutdown();
		for (Thread thread : _threads)
		{
			try
			{
				thread.join();
			}
			catch (InterruptedException e)
			{
				// We don't use interruption.
				throw Assert.unexpected(e);
			}
		}
	}
}
