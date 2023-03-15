package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
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

	private final PrintStream _stream;
	private final IConfigFileSystem _fileSystem;
	private final LocalDataModel _sharedDataModel;
	private final IConnection _connection;
	private final MultiThreadedScheduler _scheduler;
	private final String _ipfsConnectString;
	private final String _keyName;
	private int _nextOperationCounter;
	private boolean _errorOccurred;
	private DraftManager _lazySharedDraftManager;

	public StandardEnvironment(PrintStream stream, IConfigFileSystem fileSystem, IConnection connection, String ipfsConnectString, String keyName)
	{
		_internalLock = new ReentrantLock();
		_stream = stream;
		_fileSystem = fileSystem;
		_sharedDataModel = new LocalDataModel(fileSystem);
		_connection = connection;
		_scheduler = new MultiThreadedScheduler(connection, THREAD_COUNT);
		_ipfsConnectString = ipfsConnectString;
		_keyName = keyName;
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
	public INetworkScheduler getSharedScheduler()
	{
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
	 * Shuts down the scheduler and drops all associated caches.
	 */
	public void shutdown()
	{
		_sharedDataModel.dropAllCaches();
		_scheduler.shutdown();
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
	public String getIpfsConnectString()
	{
		return _ipfsConnectString;
	}

	@Override
	public String getKeyName()
	{
		return _keyName;
	}

	@Override
	public long currentTimeMillis()
	{
		return System.currentTimeMillis();
	}
}
