package com.jeffdisher.cacophony.scheduler;

import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


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
			_threads[i] = new Thread(() -> {
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
			});
			_threads[i].setName("Scheduler thread #" + i);
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
	public FutureSave saveStream(InputStream stream, boolean shouldCloseStream)
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
		};
		_queue.enqueue(r);
		return future;
	}

	@Override
	public FuturePublish publishIndex(IpfsFile indexHash)
	{
		FuturePublish future = new FuturePublish(indexHash);
		Runnable r = () -> {
			IpfsConnectionException error = _remote.publishIndex(indexHash);
			if (null == error)
			{
				future.success();
			}
			else
			{
				future.failure(error);
			}
		};
		_queue.enqueue(r);
		return future;
	}

	@Override
	public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
	{
		FutureResolve future = new FutureResolve();
		Runnable r = () -> {
			IpfsFile resolved = _remote.resolvePublicKey(keyToResolve);
			if (null != resolved)
			{
				future.success(resolved);
			}
			else
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
	public IpfsKey getPublicKey()
	{
		// This is just a wrapper since we don't want RemoteActions exposed.
		return _remote.getPublicKey();
	}

	@Override
	public FuturePin pin(IpfsFile cid)
	{
		FuturePin future = new FuturePin();
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
