package com.jeffdisher.cacophony.scheduler;

import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.KeyNameRules;
import com.jeffdisher.cacophony.utils.MiscHelpers;


/**
 * An implementation of INetworkScheduler which runs all calls on a set of background threads, via a work queue.
 * It relies on being explicitly shut down in order to stop running.
 */
public class MultiThreadedScheduler implements INetworkScheduler
{
	private final IConnection _ipfs;
	private final WorkQueue _queue;
	private final Thread[] _threads;

	public MultiThreadedScheduler(IConnection ipfs, int threadCount)
	{
		_ipfs = ipfs;
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
				byte[] data = _ipfs.loadData(file);
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
		boolean didEnqueue = _queue.enqueue(r);
		if (!didEnqueue)
		{
			future.failureInConnection(WorkQueue.createShutdownError());
		}
		return future;
	}

	@Override
	public <R> FutureSizedRead<R> readDataWithSizeCheck(IpfsFile file, String context, long maxSizeInBytes, DataDeserializer<R> decoder)
	{
		Assert.assertTrue(maxSizeInBytes > 0L);
		FutureSizedRead<R> future = new FutureSizedRead<R>();
		Runnable r = () -> {
			try
			{
				long sizeInBytes = _ipfs.getSizeInBytes(file);
				if (sizeInBytes <= maxSizeInBytes)
				{
					byte[] data = _ipfs.loadData(file);
					future.success(decoder.apply(data));
				}
				else
				{
					// Size was greater than limit so synthesize the exception.
					future.failureInSizeCheck(new SizeConstraintException(context, sizeInBytes, maxSizeInBytes));
				}
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
		boolean didEnqueue = _queue.enqueue(r);
		if (!didEnqueue)
		{
			future.failureInConnection(WorkQueue.createShutdownError());
		}
		return future;
	}

	@Override
	public FutureSave saveStream(InputStream stream)
	{
		FutureSave future = new FutureSave();
		Runnable r = () -> {
			try
			{
				IpfsFile file = _ipfs.storeData(stream);
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
		boolean didEnqueue = _queue.enqueue(r);
		if (!didEnqueue)
		{
			future.failure(WorkQueue.createShutdownError());
		}
		return future;
	}

	@Override
	public FuturePublish publishIndex(String keyName, IpfsKey publicKey, IpfsFile indexHash)
	{
		// We expect that the caller already validated this name.
		Assert.assertTrue(KeyNameRules.isValidKey(keyName));
		Assert.assertTrue(null != publicKey);
		Assert.assertTrue(null != indexHash);
		FuturePublish future = new FuturePublish(indexHash);
		Runnable r = () -> {
			try
			{
				_ipfs.publish(keyName, publicKey, indexHash);
				future.success();
			}
			catch (IpfsConnectionException e)
			{
				future.failure(e);
			}
		};
		boolean didEnqueue = _queue.enqueue(r);
		if (!didEnqueue)
		{
			future.failure(WorkQueue.createShutdownError());
		}
		return future;
	}

	@Override
	public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
	{
		Assert.assertTrue(null != keyToResolve);
		FutureResolve future = new FutureResolve(keyToResolve);
		Runnable r = () -> {
			try
			{
				IpfsFile resolved = _ipfs.resolve(keyToResolve);
				// This should only fail with an exception.
				Assert.assertTrue(null != resolved);
				future.success(resolved);
			}
			catch (IpfsConnectionException e)
			{
				future.failure(e);
			}
		};
		boolean didEnqueue = _queue.enqueue(r);
		if (!didEnqueue)
		{
			future.failure(WorkQueue.createShutdownError());
		}
		return future;
	}

	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		FutureSize future = new FutureSize();
		Runnable r = () -> {
			try
			{
				long sizeInBytes = _ipfs.getSizeInBytes(cid);
				future.success(sizeInBytes);
			}
			catch (IpfsConnectionException e)
			{
				future.failure(e);
			}
		};
		boolean didEnqueue = _queue.enqueue(r);
		if (!didEnqueue)
		{
			future.failure(WorkQueue.createShutdownError());
		}
		return future;
	}

	@Override
	public FuturePin pin(IpfsFile cid)
	{
		FuturePin future = new FuturePin(cid);
		Runnable r = () -> {
			try
			{
				_ipfs.pin(cid);
				future.success();
			}
			catch (IpfsConnectionException e)
			{
				future.failure(e);
			}
		};
		boolean didEnqueue = _queue.enqueue(r);
		if (!didEnqueue)
		{
			future.failure(WorkQueue.createShutdownError());
		}
		return future;
	}

	@Override
	public FutureVoid unpin(IpfsFile cid)
	{
		FutureVoid future = new FutureVoid();
		Runnable r = () -> {
			try
			{
				_ipfs.rm(cid);
				future.success();
			}
			catch (IpfsConnectionException e)
			{
				future.failure(e);
			}
		};
		boolean didEnqueue = _queue.enqueue(r);
		if (!didEnqueue)
		{
			future.failure(WorkQueue.createShutdownError());
		}
		return future;
	}

	@Override
	public FutureKey getOrCreatePublicKey(String keyName)
	{
		// We expect that the caller already validated this name.
		Assert.assertTrue(KeyNameRules.isValidKey(keyName));
		FutureKey future = new FutureKey();
		Runnable r = () -> {
			try
			{
				IpfsKey publicKey = _ipfs.getOrCreatePublicKey(keyName);
				future.success(publicKey);
			}
			catch (IpfsConnectionException e)
			{
				future.failureInConnection(e);
			}
		};
		boolean didEnqueue = _queue.enqueue(r);
		if (!didEnqueue)
		{
			future.failureInConnection(WorkQueue.createShutdownError());
		}
		return future;
	}

	@Override
	public FutureVoid deletePublicKey(String keyName)
	{
		FutureVoid future = new FutureVoid();
		Runnable r = () -> {
			try
			{
				_ipfs.deletePublicKey(keyName);
				future.success();
			}
			catch (IpfsConnectionException e)
			{
				future.failure(e);
			}
		};
		boolean didEnqueue = _queue.enqueue(r);
		if (!didEnqueue)
		{
			future.failure(WorkQueue.createShutdownError());
		}
		return future;
	}

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
