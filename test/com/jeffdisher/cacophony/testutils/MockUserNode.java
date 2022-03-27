package com.jeffdisher.cacophony.testutils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


/**
 * This is just the combination of all the elements normally associated with a single node and single channel/user.
 */
public class MockUserNode
{
	private static final String IPFS_HOST = "ipfsHost";

	private final MockConnection _sharedConnection;
	private final LocalConfig _config;
	private final StandardEnvironment _executor;

	public MockUserNode(String keyName, IpfsKey key, MockUserNode upstreamUserNode)
	{
		_executor = new StandardEnvironment(System.out);
		MockConnection upstreamConnection = (null != upstreamUserNode) ? upstreamUserNode._sharedConnection : null;
		_sharedConnection = new MockConnection(keyName, key, upstreamConnection);
		_config = new LocalConfig(new MemoryConfigFileSystem(), new MockConnectionFactory(_sharedConnection));
	}

	public void createChannel(String keyName, String name, String description, byte[] userPicData) throws Throwable
	{
		File userPic = File.createTempFile("cacophony", "test");
		FileOutputStream stream = new FileOutputStream(userPic);
		stream.write(userPicData);
		stream.close();
		
		CreateChannelCommand createChannel = new CreateChannelCommand(IPFS_HOST, keyName);
		createChannel.runInEnvironment(_executor, _config);
		UpdateDescriptionCommand updateDescription = new UpdateDescriptionCommand(name, description, userPic);
		updateDescription.runInEnvironment(_executor, _config);
	}

	public void runCommand(OutputStream captureStream, ICommand command) throws Throwable
	{
		StandardEnvironment executor = _executor;
		// See if we want to override the output capture.
		if (null != captureStream)
		{
			executor = new StandardEnvironment(new PrintStream(captureStream));
		}
		command.runInEnvironment(executor, _config);
	}

	public byte[] loadDataFromNode(IpfsFile cid) throws IpfsConnectionException
	{
		return _sharedConnection.loadData(cid);
	}

	public IpfsFile resolveKeyOnNode(IpfsKey key) throws IpfsConnectionException
	{
		return _sharedConnection.resolve(key);
	}

	public LocalIndex getLocalStoredIndex() throws UsageException
	{
		return _config.readExistingSharedIndex();
	}

	public boolean isPinnedLocally(IpfsFile file)
	{
		return _sharedConnection.isPinned(file);
	}

	public boolean isInPinCache(IpfsFile file)
	{
		return _config.loadGlobalPinCache().isCached(file);
	}

	public GlobalPrefs readPrefs()
	{
		return _config.readSharedPrefs();
	}
}
