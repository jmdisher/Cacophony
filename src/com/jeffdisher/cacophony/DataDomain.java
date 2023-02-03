package com.jeffdisher.cacophony;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.logic.IConnectionFactory;
import com.jeffdisher.cacophony.logic.RealConfigFileSystem;
import com.jeffdisher.cacophony.logic.RealConnectionFactory;
import com.jeffdisher.cacophony.logic.Uploader;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public class DataDomain implements Closeable
{
	public static final String DEFAULT_STORAGE_DIRECTORY_NAME = ".cacophony";
	// We use a lockfile with a name based on the config directory name.  Note that it can't be inside the directory
	// since it may not exist yet at this level.  This has the side-effect of protecting against concurrent creation.
	public static final String CONFIG_LOCK_SUFFIX = "_lock";

	public static DataDomain detectDataDomain()
	{
		return _realDirectory();
	}

	private static DataDomain _realDirectory()
	{
		// Note that we default to "~/.cacophony" unless they provide the CACOPHONY_STORAGE environment variable.
		File storageDirectory = null;
		String envVarStorage = System.getenv(EnvVars.ENV_VAR_CACOPHONY_STORAGE);
		if (null != envVarStorage)
		{
			storageDirectory = new File(envVarStorage);
		}
		else
		{
			File homeDirectory = new File(System.getProperty("user.home"));
			Assert.assertTrue(homeDirectory.exists());
			storageDirectory = new File(homeDirectory, DEFAULT_STORAGE_DIRECTORY_NAME);
		}
		return new DataDomain(storageDirectory);
	}


	private final File _realDirectory;
	private final Uploader _uploader;
	private final IConnectionFactory _connectionFactory;
	private final IConfigFileSystem _fileSystem;

	private DataDomain(File realDirectory)
	{
		Assert.assertTrue(null != realDirectory);
		_realDirectory = realDirectory;
		_uploader = new Uploader();
		try
		{
			_uploader.start();
		}
		catch (Exception e)
		{
			// We don't know how this would fail, here.
			throw Assert.unexpected(e);
		}
		_connectionFactory = new RealConnectionFactory(_uploader);
		_fileSystem = new RealConfigFileSystem(_realDirectory);
	}

	public IConnectionFactory getConnectionFactory()
	{
		return _connectionFactory;
	}

	public IConfigFileSystem getFileSystem()
	{
		return _fileSystem;
	}

	public Lock lock() throws UsageException
	{
		FileChannel lockedChannel = _lockFile(_realDirectory);
		return new Lock(lockedChannel);
	}

	@Override
	public void close()
	{
		try
		{
			_uploader.stop();
		}
		catch (Exception e)
		{
			// We don't know how this would fail, here.
			throw Assert.unexpected(e);
		}
	}


	private FileChannel _lockFile(File storageDirectory) throws UsageException
	{
		File lockFile = new File(storageDirectory.getParentFile(), storageDirectory.getName() + CONFIG_LOCK_SUFFIX);
		FileChannel channel;
		try
		{
			// We will open the lock file with write permission to get the exclusive lock.
			channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
		}
		catch (IOException e)
		{
			// This is a usage problem.
			throw new UsageException("Failure opening lock file: " + e.getLocalizedMessage());
		}
		try
		{
			// Create the lock - we rely on just closing the channel to release the lock since we otherwise need to return both.
			FileLock lock = channel.tryLock();
			if (null == lock)
			{
				throw new UsageException("Failed to acquire file lock (is Cacophony already running?): " + lockFile.getAbsolutePath());
			}
		}
		catch (IOException e)
		{
			// Not sure what would cause this here.
			Assert.unexpected(e);
		}
		return channel;
	}


	public class Lock implements AutoCloseable
	{
		private final FileChannel _lockedChannelOrNull;
		
		private Lock(FileChannel lockedChannelOrNull)
		{
			_lockedChannelOrNull = lockedChannelOrNull;
		}
		
		@Override
		public void close() throws IOException
		{
			if (null != _lockedChannelOrNull)
			{
				_lockedChannelOrNull.close();
			}
		}
	}
}
