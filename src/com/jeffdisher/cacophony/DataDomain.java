package com.jeffdisher.cacophony;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.logic.IConnectionFactory;
import com.jeffdisher.cacophony.logic.RealConfigFileSystem;
import com.jeffdisher.cacophony.logic.RealConnectionFactory;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.logic.Uploader;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.testutils.MockConnectionFactory;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This exists just as a way to abstract some of the details of how the storage directory is managed since
 * ENV_VAR_CACOPHONY_ENABLE_FAKE_SYSTEM means that might be fully in-memory with a fake lock.
 */
public class DataDomain implements Closeable
{
	public static final String DEFAULT_STORAGE_DIRECTORY_NAME = ".cacophony";
	// We use a lockfile with a name based on the config directory name.  Note that it can't be inside the directory
	// since it may not exist yet at this level.  This has the side-effect of protecting against concurrent creation.
	public static final String CONFIG_LOCK_SUFFIX = "_lock";

	public static DataDomain detectDataDomain()
	{
		// See if we are using the "fake" system (typically used in UI testing).
		boolean shouldEnableFakeSystem = (null != System.getenv(EnvVars.ENV_VAR_CACOPHONY_ENABLE_FAKE_SYSTEM));
		return shouldEnableFakeSystem
				? _fakeDirectory()
				: _realDirectory()
		;
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

	private static DataDomain _fakeDirectory()
	{
		return new DataDomain(null);
	}

	private static MockSingleNode _buildOurTestNode(MemoryConfigFileSystem ourFileSystem) throws CacophonyException
	{
		// We will just make a basic system with 2 users.  This might expand in the future.
		MockSwarm swarm = new MockSwarm();
		MockSingleNode them = new MockSingleNode(swarm);
		IpfsKey theirKey = IpfsKey.fromPublicKey("z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV");
		them.addNewKey("key", theirKey);
		MockConnectionFactory theirConnection = new MockConnectionFactory(them);
		StandardEnvironment theirEnv = new StandardEnvironment(new PrintStream(new ByteArrayOutputStream())
				, new MemoryConfigFileSystem(null)
				, theirConnection
				, false
		);
		new CreateChannelCommand("ipfs", "key").runInEnvironment(theirEnv);
		new UpdateDescriptionCommand("them", "the other user", null, null, "other.site").runInEnvironment(theirEnv);
		new PublishCommand("post1", "some description of the post", null, new ElementSubCommand[0]).runInEnvironment(theirEnv);
		theirEnv.shutdown();
		
		MockSingleNode us = new MockSingleNode(swarm);
		IpfsKey ourKey = IpfsKey.fromPublicKey("z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3Qx1");
		us.addNewKey("key", ourKey);
		MockConnectionFactory ourConnection = new MockConnectionFactory(us);
		StandardEnvironment ourEnv = new StandardEnvironment(new PrintStream(new ByteArrayOutputStream())
				, ourFileSystem
				, ourConnection
				, false
		);
		new CreateChannelCommand("ipfs", "key").runInEnvironment(ourEnv);
		new UpdateDescriptionCommand("us", "the main user", null, "email", null).runInEnvironment(ourEnv);
		new StartFollowingCommand(theirKey).runInEnvironment(ourEnv);
		ourEnv.shutdown();
		
		return us;
	}


	private final File _realDirectoryOrNull;
	private final Uploader _uploaderOrNull;
	private final IConnectionFactory _connectionFactory;
	private final IConfigFileSystem _fileSystem;

	private DataDomain(File realDirectoryOrNull)
	{
		_realDirectoryOrNull = realDirectoryOrNull;
		if (null != realDirectoryOrNull)
		{
			_uploaderOrNull = new Uploader();
			try
			{
				_uploaderOrNull.start();
			}
			catch (Exception e)
			{
				// We don't know how this would fail, here.
				throw Assert.unexpected(e);
			}
			_connectionFactory = new RealConnectionFactory(_uploaderOrNull);
			_fileSystem = new RealConfigFileSystem(_realDirectoryOrNull);
		}
		else
		{
			MemoryConfigFileSystem ourFileSystem = new MemoryConfigFileSystem(null);
			_uploaderOrNull = null;
			MockSingleNode ourNode;
			try
			{
				ourNode = _buildOurTestNode(ourFileSystem);
			}
			catch (CacophonyException e)
			{
				// We don't expect this error when building test cluster.
				throw Assert.unexpected(e);
			}
			_connectionFactory = new MockConnectionFactory(ourNode);
			_fileSystem = ourFileSystem;
		}
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
		FileChannel lockedChannel = (null != _realDirectoryOrNull)
				? _lockFile(_realDirectoryOrNull)
				: null
		;
		return new Lock(lockedChannel);
	}

	@Override
	public void close()
	{
		if (null != _uploaderOrNull)
		{
			try
			{
				_uploaderOrNull.stop();
			}
			catch (Exception e)
			{
				// We don't know how this would fail, here.
				throw Assert.unexpected(e);
			}
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