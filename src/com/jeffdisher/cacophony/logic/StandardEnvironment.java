package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.Assert;


public class StandardEnvironment implements IEnvironment
{
	// No hard science was used to derive this.  We are just using 4 background threads as a starting-point.
	private static final int THREAD_COUNT = 4;

	// This lock is used to protect internal variables which may be changed by multiple threads.
	private final Lock _internalLock;

	private final PrintStream _stream;
	private final IConfigFileSystem _fileSystem;
	private final LocalDataModel _sharedDataModel;
	private final IConnectionFactory _factory;
	private final boolean _shouldEnableVerifications;
	private int _nextOperationCounter;
	private boolean _errorOccurred;
	private INetworkScheduler _lazySharedScheduler;
	private DraftManager _lazySharedDraftManager;

	public StandardEnvironment(PrintStream stream, IConfigFileSystem fileSystem, IConnectionFactory factory, boolean shouldEnableVerifications)
	{
		_internalLock = new ReentrantLock();
		_stream = stream;
		_fileSystem = fileSystem;
		_sharedDataModel = new LocalDataModel(fileSystem);
		_factory = factory;
		_shouldEnableVerifications = shouldEnableVerifications;
		_nextOperationCounter = 0;
	}

	@Override
	public void logToConsole(String message)
	{
		_stream.println(message);
	}

	/**
	 * Used to create a logging object associated with this opening message so that the completion of the operation will
	 * be associated with it.
	 * 
	 * @param openingMessage The message to log when opening the log option.
	 * @return An object which can receive the log message for when this is finished.
	 */
	@Override
	public IOperationLog logOperation(String openingMessage)
	{
		int operationNumber = _nextOperationCounter + 1;
		_nextOperationCounter += 1;
		_stream.println(">" + operationNumber + " " + openingMessage);
		return (finishMessage) -> _stream.println("<" + operationNumber + " " + finishMessage);
	}

	@Override
	public void logError(String message)
	{
		System.err.println(message);
		_errorOccurred = true;
	}

	public boolean didErrorOccur()
	{
		return _errorOccurred;
	}

	@Override
	public INetworkScheduler getSharedScheduler(IConnection ipfs, String keyName) throws IpfsConnectionException
	{
		INetworkScheduler scheduler = null;
		_internalLock.lock();
		try
		{
			if (null == _lazySharedScheduler)
			{
				_lazySharedScheduler = new MultiThreadedScheduler(RemoteActions.loadIpfsConfig(ipfs, keyName, _shouldEnableVerifications), THREAD_COUNT);
			}
			scheduler = _lazySharedScheduler;
		}
		finally
		{
			_internalLock.unlock();
		}
		return scheduler;
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
	 * Shuts down the scheduler and drops all associated caches.
	 */
	public void shutdown()
	{
		_sharedDataModel.dropAllCaches();
		if (null != _lazySharedScheduler)
		{
			_lazySharedScheduler.shutdown();
			_lazySharedScheduler = null;
		}
	}

	@Override
	public IConfigFileSystem getConfigFileSystem()
	{
		return _fileSystem;
	}

	@Override
	public IConnectionFactory getConnectionFactory()
	{
		return _factory;
	}
}
