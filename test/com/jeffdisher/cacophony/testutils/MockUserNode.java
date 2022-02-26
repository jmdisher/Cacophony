package com.jeffdisher.cacophony.testutils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * This is just the combination of all the elements normally associated with a single node and single channel/user.
 */
public class MockUserNode
{
	private static final String IPFS_HOST = "ipfsHost";

	private final Executor _executor;
	private final GlobalPinCache _pinCache;
	private final MockPinMechanism _pinMechanism;
	private final FollowIndex _followIndex;
	private final MockConnection _sharedConnection;
	private final MockLocalActions _localActions;

	public MockUserNode(String keyName, IpfsKey key, MockUserNode upstreamUserNode)
	{
		_executor = new Executor(System.out);
		MockConnection upstreamConnection = (null != upstreamUserNode) ? upstreamUserNode._sharedConnection : null;
		_pinCache = GlobalPinCache.newCache();
		_pinMechanism = new MockPinMechanism(upstreamConnection);
		_followIndex = FollowIndex.emptyFollowIndex();
		_sharedConnection = new MockConnection(keyName, key, _pinMechanism, upstreamConnection);
		_localActions = new MockLocalActions(null, null, null, _sharedConnection, _pinCache, _pinMechanism, _followIndex);
	}

	public void createChannel(String keyName, String name, String description, byte[] userPicData) throws IOException
	{
		File userPic = File.createTempFile("cacophony", "test");
		FileOutputStream stream = new FileOutputStream(userPic);
		stream.write(userPicData);
		stream.close();
		
		CreateChannelCommand createChannel = new CreateChannelCommand(IPFS_HOST, keyName);
		createChannel.scheduleActions(_executor, _localActions);
		UpdateDescriptionCommand updateDescription = new UpdateDescriptionCommand(name, description, userPic);
		updateDescription.scheduleActions(_executor, _localActions);
	}

	public void runCommand(Executor specialExecutor, ICommand command) throws Throwable
	{
		command.scheduleActions((null != specialExecutor) ? specialExecutor : _executor, _localActions);
	}

	public byte[] loadDataFromNode(IpfsFile cid) throws IOException
	{
		return _sharedConnection.loadData(cid);
	}

	public IpfsFile resolveKeyOnNode(IpfsKey key) throws IOException
	{
		return _sharedConnection.resolve(key);
	}

	public LocalIndex getLocalStoredIndex()
	{
		return _localActions.getStoredIndex();
	}

	public boolean isPinnedLocally(IpfsFile file)
	{
		return _pinMechanism.isPinned(file);
	}
}