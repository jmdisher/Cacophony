package com.jeffdisher.cacophony.testutils;

import java.io.File;
import java.io.FileOutputStream;

import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * This is just the combination of all the elements normally associated with a single node and single channel/user.
 */
public class MockUserNode
{
	private static final String IPFS_HOST = "ipfsHost";

	private final StandardEnvironment _executor;
	private final MockConnection _sharedConnection;
	private final MockLocalActions _localActions;

	public MockUserNode(String keyName, IpfsKey key, MockUserNode upstreamUserNode)
	{
		_executor = new StandardEnvironment(System.out);
		MockConnection upstreamConnection = (null != upstreamUserNode) ? upstreamUserNode._sharedConnection : null;
		_sharedConnection = new MockConnection(keyName, key, upstreamConnection);
		_localActions = new MockLocalActions(null, null, null, _sharedConnection);
	}

	public void createChannel(String keyName, String name, String description, byte[] userPicData) throws Throwable
	{
		File userPic = File.createTempFile("cacophony", "test");
		FileOutputStream stream = new FileOutputStream(userPic);
		stream.write(userPicData);
		stream.close();
		
		CreateChannelCommand createChannel = new CreateChannelCommand(IPFS_HOST, keyName);
		createChannel.runInEnvironment(_executor, _localActions);
		UpdateDescriptionCommand updateDescription = new UpdateDescriptionCommand(name, description, userPic);
		updateDescription.runInEnvironment(_executor, _localActions);
	}

	public void runCommand(StandardEnvironment specialExecutor, ICommand command) throws Throwable
	{
		command.runInEnvironment((null != specialExecutor) ? specialExecutor : _executor, _localActions);
	}

	public byte[] loadDataFromNode(IpfsFile cid) throws IpfsConnectionException
	{
		return _sharedConnection.loadData(cid);
	}

	public IpfsFile resolveKeyOnNode(IpfsKey key) throws IpfsConnectionException
	{
		return _sharedConnection.resolve(key);
	}

	public LocalIndex getLocalStoredIndex()
	{
		return _localActions.getStoredIndex();
	}

	public boolean isPinnedLocally(IpfsFile file)
	{
		return _sharedConnection.isPinned(file);
	}

	public boolean isInPinCache(IpfsFile file)
	{
		return _localActions.loadGlobalPinCache().isCached(file);
	}

	public GlobalPrefs readPrefs()
	{
		return _localActions.readSharedPrefs();
	}
}
