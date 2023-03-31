package com.jeffdisher.cacophony.testutils;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This is just the combination of all the elements normally associated with a single node and single channel/user.
 */
public class MockUserNode
{
	private static final String IPFS_HOST = "ipfsHost";

	private final String _localKeyName;
	private final IpfsKey _publicKey;
	private final MockSingleNode _sharedConnection;
	private final MemoryConfigFileSystem _fileSystem;
	private final StandardEnvironment _executor;

	public MockUserNode(String keyName, IpfsKey key, MockSingleNode node)
	{
		_localKeyName = keyName;
		_publicKey = key;
		_sharedConnection = node;
		_sharedConnection.addNewKey(keyName, key);
		_fileSystem = new MemoryConfigFileSystem(null);
		_executor = new StandardEnvironment(System.out, _fileSystem, _sharedConnection, keyName, key);
	}

	public void createEmptyConfig(String keyName) throws UsageException, IpfsConnectionException
	{
		StandardAccess.createNewChannelConfig(_executor, IPFS_HOST, keyName);
	}

	public void createChannel(String keyName, String name, String description, byte[] userPicData) throws Throwable
	{
		StandardAccess.createNewChannelConfig(_executor, IPFS_HOST, keyName);
		
		ByteArrayInputStream pictureStream = new ByteArrayInputStream(userPicData);
		
		CreateChannelCommand createChannel = new CreateChannelCommand(_localKeyName);
		ICommand.Result result = createChannel.runInEnvironment(_executor);
		_handleResult(result);
		UpdateDescriptionCommand updateDescription = new UpdateDescriptionCommand(name, description, pictureStream, null, null);
		result = updateDescription.runInEnvironment(_executor);
		_handleResult(result);
	}

	/**
	 * Runs the given command on this mock node.
	 * 
	 * @param captureStream The stream to use to override the logging output (can be null).
	 * @param command The command to run.
	 * @return True on success, false if the command failed.
	 * @throws Throwable Something went wrong which wasn't just a simple failed command.
	 */
	public boolean runCommand(OutputStream captureStream, ICommand<?> command) throws Throwable
	{
		StandardEnvironment executor = _executor;
		// See if we want to override the output capture.
		boolean isNew = false;
		if (null != captureStream)
		{
			executor = new StandardEnvironment(new PrintStream(captureStream), _fileSystem, _sharedConnection, _localKeyName, _publicKey);
			isNew = true;
		}
		ICommand.Result result = command.runInEnvironment(executor);
		_handleResult(result);
		if (isNew)
		{
			executor.shutdown();
		}
		return !executor.didErrorOccur();
	}

	public byte[] loadDataFromNode(IpfsFile cid) throws IpfsConnectionException
	{
		return _sharedConnection.loadDataFromNode(cid);
	}

	public IpfsFile resolveKeyOnNode(IpfsKey key) throws IpfsConnectionException
	{
		return _sharedConnection.resolve(key);
	}

	public IpfsFile getLastRootElement() throws IpfsConnectionException
	{
		try (IReadingAccess reading = StandardAccess.readAccess(_executor))
		{
			return reading.getLastRootElement();
		}
	}

	public boolean isPinnedLocally(IpfsFile file)
	{
		return _sharedConnection.isPinned(file);
	}

	public boolean isInPinCache(IpfsFile file) throws IpfsConnectionException
	{
		IReadingAccess reading = StandardAccess.readAccess(_executor);
		boolean isPinned = reading.isInPinCached(file);
		reading.close();
		return isPinned;
	}

	public PrefsData readPrefs() throws IpfsConnectionException
	{
		IReadingAccess reading = StandardAccess.readAccess(_executor);
		PrefsData prefs = reading.readPrefs();
		reading.close();
		return prefs;
	}

	public IFolloweeReading readFollowIndex() throws IpfsConnectionException
	{
		// We use the write accessor since we want the full FollowIndex interface for tests (returning this outside of the access closure is incorrect, either way).
		try (IWritingAccess writing = StandardAccess.writeAccess(_executor))
		{
			return writing.readableFolloweeData();
		}
	}

	public void deleteFile(IpfsFile cid)
	{
		try
		{
			_sharedConnection.rm(cid);
		}
		catch (IpfsConnectionException e)
		{
			throw Assert.unexpected(e);
		}
	}

	public void shutdown()
	{
		_executor.shutdown();
	}

	public void timeoutKey(IpfsKey publicKey)
	{
		_sharedConnection.timeoutKey(publicKey);
	}


	private void _handleResult(ICommand.Result result) throws IpfsConnectionException
	{
		IpfsFile rootToPublish = result.getIndexToPublish();
		if (null != rootToPublish)
		{
			_sharedConnection.publish(_localKeyName, _publicKey, rootToPublish);
		}
	}
}
