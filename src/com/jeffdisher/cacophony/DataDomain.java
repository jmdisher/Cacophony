package com.jeffdisher.cacophony;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.RefreshFolloweeCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.IpfsConnection;
import com.jeffdisher.cacophony.logic.RealConfigFileSystem;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.logic.Uploader;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.SilentLogger;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;

import io.ipfs.api.IPFS;
import io.ipfs.multiaddr.MultiAddress;


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
	// This is the default used by IPFS.java (timeout to establish connection.
	public static final int CONNECTION_TIMEOUT_MILLIS = 10_000;
	// The default wait for response in IPFS.java is 1 minute but pin could take a long time so we use 30 minutes.
	// (this value isn't based on any solid science so it may change in the future).
	public static final int LONG_READ_TIMEOUT_MILLIS = 30 * 60 * 1000;

	public static DataDomain detectDataDomain()
	{
		// See if we are using the "fake" system (typically used in UI testing).
		String fakeDirectory = System.getenv(EnvVars.ENV_VAR_CACOPHONY_ENABLE_FAKE_SYSTEM);
		return (null != fakeDirectory)
				? _fakeDirectory(fakeDirectory)
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
		return new DataDomain(storageDirectory, null);
	}

	private static DataDomain _fakeDirectory(String fakeDirectory)
	{
		File directory = new File(fakeDirectory);
		Assert.assertTrue(directory.isDirectory());
		return new DataDomain(null, directory);
	}

	private static MockSingleNode _buildOurTestNode(MemoryConfigFileSystem ourFileSystem) throws CacophonyException
	{
		// We will just make a basic system with 2 users.  This might expand in the future.
		MockSwarm swarm = new MockSwarm();
		// Just use the normal default IPFS connect.
		String ipfsConnectString = "/ip4/127.0.0.1/tcp/5001";
		String keyName = "Cacophony";
		MockSingleNode them = new MockSingleNode(swarm);
		IpfsKey theirKey = IpfsKey.fromPublicKey("z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV");
		them.addNewKey(keyName, theirKey);
		StandardEnvironment theirEnv = new StandardEnvironment(new MemoryConfigFileSystem(null)
				, them
				, keyName
				, theirKey
		);
		ILogger theirLogger = new SilentLogger();
		ICommand.Context theirContext = new ICommand.Context(theirEnv, theirLogger, null, null, null);
		StandardAccess.createNewChannelConfig(theirEnv, ipfsConnectString, keyName);
		new CreateChannelCommand(keyName).runInContext(theirContext);
		new UpdateDescriptionCommand("them", "the other user", null, null, "other.site").runInContext(theirContext);
		ICommand.Result result = new PublishCommand("post1", "some description of the post", null, new ElementSubCommand[0]).runInContext(theirContext);
		IpfsFile newRoot = result.getIndexToPublish();
		them.publish(keyName, theirKey, newRoot);
		theirEnv.shutdown();
		
		MockSingleNode us = new MockSingleNode(swarm);
		IpfsKey ourKey = IpfsKey.fromPublicKey("z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3Qx1");
		us.addNewKey(keyName, ourKey);
		StandardEnvironment ourEnv = new StandardEnvironment(ourFileSystem
				, us
				, keyName
				, ourKey
		);
		ILogger ourLogger = new SilentLogger();
		ICommand.Context ourContext = new ICommand.Context(ourEnv, ourLogger, null, null, null);
		StandardAccess.createNewChannelConfig(ourEnv, ipfsConnectString, keyName);
		new CreateChannelCommand(keyName).runInContext(ourContext);
		result = new UpdateDescriptionCommand("us", "the main user", null, "email", null).runInContext(ourContext);
		us.publish(keyName, ourKey, newRoot);
		new StartFollowingCommand(theirKey).runInContext(ourContext);
		// (for version 2.1, start follow doesn't fetch the data)
		new RefreshFolloweeCommand(theirKey).runInContext(ourContext);
		ourEnv.shutdown();
		
		return us;
	}


	private final File _realDirectoryOrNull;
	private final Uploader _uploaderOrNull;
	private final MockSingleNode _mockNodeOrNull;
	private final IConfigFileSystem _fileSystem;

	private DataDomain(File realDirectoryOrNull, File fakeDirectoryOrNull)
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
			_mockNodeOrNull = null;
			_fileSystem = new RealConfigFileSystem(_realDirectoryOrNull);
		}
		else
		{
			Assert.assertTrue(null != fakeDirectoryOrNull);
			MemoryConfigFileSystem ourFileSystem = new MemoryConfigFileSystem(fakeDirectoryOrNull);
			_uploaderOrNull = null;
			try
			{
				_mockNodeOrNull = _buildOurTestNode(ourFileSystem);
			}
			catch (CacophonyException e)
			{
				// We don't expect this error when building test cluster.
				throw Assert.unexpected(e);
			}
			_fileSystem = ourFileSystem;
		}
	}

	public IConnection buildSharedConnection(String ipfsConnectString) throws IpfsConnectionException
	{
		IConnection connection;
		if (null == _mockNodeOrNull)
		{
			// Real connection.
			try {
				IPFS defaultConnection = new IPFS(ipfsConnectString);
				@SuppressWarnings("unchecked")
				Map<String, Object> addresses = (Map<String, Object>) defaultConnection.config.get("Addresses");
				String result = (String) addresses.get("Gateway");
				// This "Gateway" is of the form:  /ip4/127.0.0.1/tcp/8080
				int gatewayPort = Integer.parseInt(result.split("/")[4]);
				
				MultiAddress addr = new MultiAddress(ipfsConnectString);
				IPFS longWaitConnection = new IPFS(addr.getHost(), addr.getTCPPort(), "/api/v0/", CONNECTION_TIMEOUT_MILLIS, LONG_READ_TIMEOUT_MILLIS, false);
				connection = new IpfsConnection(_uploaderOrNull, defaultConnection, longWaitConnection, gatewayPort);
			}
			catch (IOException e)
			{
				// This happens if we fail to read the config, which should only happen if the node is bogus.
				throw new IpfsConnectionException("connect", ipfsConnectString, e);
			}
			catch (RuntimeException e)
			{
				// For some reason, "new IPFS" throws a RuntimeException, instead of IOException, if the connection fails.
				throw new IpfsConnectionException("connect", ipfsConnectString, new IOException(e));
			}
		}
		else
		{
			// Fake connection.
			connection = _mockNodeOrNull;
		}
		return connection;
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
