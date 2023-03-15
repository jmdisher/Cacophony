package com.jeffdisher.cacophony.testutils;

import java.io.File;
import java.io.FileOutputStream;
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
		_executor = new StandardEnvironment(System.out, _fileSystem, _sharedConnection, IPFS_HOST, keyName, key);
	}

	public void createChannel(String keyName, String name, String description, byte[] userPicData) throws Throwable
	{
		File userPic = File.createTempFile("cacophony", "test");
		FileOutputStream stream = new FileOutputStream(userPic);
		stream.write(userPicData);
		stream.close();
		
		CreateChannelCommand createChannel = new CreateChannelCommand(IPFS_HOST, _localKeyName);
		createChannel.runInEnvironment(_executor);
		UpdateDescriptionCommand updateDescription = new UpdateDescriptionCommand(name, description, userPic, null, null);
		updateDescription.runInEnvironment(_executor);
	}

	public void runCommand(OutputStream captureStream, ICommand command) throws Throwable
	{
		StandardEnvironment executor = _executor;
		// See if we want to override the output capture.
		boolean isNew = false;
		if (null != captureStream)
		{
			executor = new StandardEnvironment(new PrintStream(captureStream), _fileSystem, _sharedConnection, IPFS_HOST, _localKeyName, _publicKey);
			isNew = true;
		}
		command.runInEnvironment(executor);
		if (isNew)
		{
			executor.shutdown();
		}
	}

	public byte[] loadDataFromNode(IpfsFile cid) throws IpfsConnectionException
	{
		return _sharedConnection.loadDataFromNode(cid);
	}

	public IpfsFile resolveKeyOnNode(IpfsKey key) throws IpfsConnectionException
	{
		return _sharedConnection.resolve(key);
	}

	public IpfsFile getLastRootElement() throws UsageException, IpfsConnectionException
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

	public boolean isInPinCache(IpfsFile file) throws UsageException, IpfsConnectionException
	{
		IReadingAccess reading = StandardAccess.readAccess(_executor);
		boolean isPinned = reading.isInPinCached(file);
		reading.close();
		return isPinned;
	}

	public PrefsData readPrefs() throws UsageException, IpfsConnectionException
	{
		IReadingAccess reading = StandardAccess.readAccess(_executor);
		PrefsData prefs = reading.readPrefs();
		reading.close();
		return prefs;
	}

	public IFolloweeReading readFollowIndex() throws UsageException, IpfsConnectionException
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
}
