package com.jeffdisher.cacophony.testutils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This is just the combination of all the elements normally associated with a single node and single channel/user.
 */
public class MockUserNode
{
	public static void connectNodes(MockUserNode one, MockUserNode two)
	{
		MockSingleNode.connectPeers(one._sharedConnection, two._sharedConnection);
	}

	private static final String IPFS_HOST = "ipfsHost";

	private final MockSingleNode _sharedConnection;
	private final MemoryConfigFileSystem _fileSystem;
	private final MockConnectionFactory _factory;
	private final StandardEnvironment _executor;

	public MockUserNode(String keyName, IpfsKey key, MockUserNode upstreamUserNode)
	{
		_sharedConnection = new MockSingleNode();
		_sharedConnection.addNewKey(keyName, key);
		if (null != upstreamUserNode)
		{
			MockSingleNode.connectPeers(_sharedConnection, upstreamUserNode._sharedConnection);
		}
		_fileSystem = new MemoryConfigFileSystem();
		_factory = new MockConnectionFactory(_sharedConnection);
		_executor = new StandardEnvironment(System.out, _fileSystem, _factory, true);
	}

	public void createChannel(String keyName, String name, String description, byte[] userPicData) throws Throwable
	{
		File userPic = File.createTempFile("cacophony", "test");
		FileOutputStream stream = new FileOutputStream(userPic);
		stream.write(userPicData);
		stream.close();
		
		CreateChannelCommand createChannel = new CreateChannelCommand(IPFS_HOST, keyName);
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
			executor = new StandardEnvironment(new PrintStream(captureStream), _fileSystem, _factory, true);
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

	public IpfsFile getLastRootElement() throws UsageException, VersionException, IpfsConnectionException
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

	public boolean isInPinCache(IpfsFile file) throws UsageException, VersionException, IpfsConnectionException
	{
		IReadingAccess reading = StandardAccess.readAccess(_executor);
		boolean isPinned = reading.isInPinCached(file);
		reading.close();
		return isPinned;
	}

	public GlobalPrefs readPrefs() throws UsageException, VersionException, IpfsConnectionException
	{
		IReadingAccess reading = StandardAccess.readAccess(_executor);
		GlobalPrefs prefs = reading.readGlobalPrefs();
		reading.close();
		return prefs;
	}

	public FollowIndex readFollowIndex() throws UsageException, VersionException, IpfsConnectionException
	{
		IReadingAccess reading = StandardAccess.readAccess(_executor);
		FollowIndex followIndex = reading.readOnlyFollowIndex();
		reading.close();
		return followIndex;
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
