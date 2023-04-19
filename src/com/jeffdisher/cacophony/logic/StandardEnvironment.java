package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public class StandardEnvironment implements IEnvironment
{
	// There is a very high degree of variability observed when requesting multiple pieces of remote data from the
	// network but this seems to be minimized with a larger number of threads so we use 16 instead of the earlier value
	// of 4.
	// This will likely still be tweaked in the future as more complex use-cases become common and can be tested.
	private static final int THREAD_COUNT = 16;

	// This lock is used to protect internal variables which may be changed by multiple threads.
	private final Lock _internalLock;

	private final IConfigFileSystem _fileSystem;
	private final LocalDataModel _sharedDataModel;
	private final IConnection _connection;
	private final String _keyName;
	private final IpfsKey _publicKey;

	private MultiThreadedScheduler _scheduler;
	private DraftManager _lazySharedDraftManager;

	public StandardEnvironment(IConfigFileSystem fileSystem
			, LocalDataModel sharedDataModel
			, IConnection connection
			, String keyName
			, IpfsKey publicKey
	)
	{
		_internalLock = new ReentrantLock();
		_fileSystem = fileSystem;
		_sharedDataModel = sharedDataModel;
		_connection = connection;
		_keyName = keyName;
		_publicKey = publicKey;
		_scheduler = new MultiThreadedScheduler(connection, THREAD_COUNT);
	}

	@Override
	public INetworkScheduler getSharedScheduler()
	{
		Assert.assertTrue(null != _scheduler);
		return _scheduler;
	}

	/**
	 * Builds a new DraftManager instance on top of the config's filesystem's draft directory.
	 * 
	 * @return The new DraftManager instance.
	 */
	@Override
	public DraftManager getSharedDraftManager()
	{
		DraftManager draftManager = null;
		_internalLock.lock();
		try
		{
			if (null == _lazySharedDraftManager)
			{
				_lazySharedDraftManager = new DraftManager(_fileSystem.getDraftsTopLevelDirectory());
			}
			draftManager = _lazySharedDraftManager;
		}
		catch (IOException e)
		{
			// We don't currently know how/if we should best handle this error.
			throw Assert.unexpected(e);
		}
		finally
		{
			_internalLock.unlock();
		}
		return draftManager;
	}

	@Override
	public LocalDataModel getSharedDataModel()
	{
		return _sharedDataModel;
	}

	/**
	 * Shuts down the scheduler.
	 */
	public void shutdown()
	{
		_scheduler.shutdown();
		_scheduler = null;
	}

	@Override
	public IConfigFileSystem getConfigFileSystem()
	{
		return _fileSystem;
	}

	@Override
	public IConnection getConnection()
	{
		return _connection;
	}

	@Override
	public String getKeyName()
	{
		return _keyName;
	}

	@Override
	public IpfsKey getPublicKey()
	{
		return _publicKey;
	}

	@Override
	public long currentTimeMillis()
	{
		return System.currentTimeMillis();
	}
}
