package com.jeffdisher.cacophony.scenarios;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.ListCachedElementsForFolloweeCommand;
import com.jeffdisher.cacophony.commands.MockConnection;
import com.jeffdisher.cacophony.commands.MockLocalActions;
import com.jeffdisher.cacophony.commands.MockPinMechanism;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestFollowUpdates
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME1 = "keyName1";
	private static final IpfsKey PUBLIC_KEY1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");
	private static final String KEY_NAME2 = "keyName2";
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo142");

	@Test
	public void testFirstFetchOneElement() throws IOException
	{
		Executor executor = new Executor();
		
		// Node 1.
		GlobalPinCache pinCache1 = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism1 = new MockPinMechanism(null);
		FollowIndex followIndex1 = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection1 = new MockConnection(KEY_NAME1, PUBLIC_KEY1, pinMechanism1);
		MockLocalActions localActions1 = new MockLocalActions(null, null, sharedConnection1, pinCache1, pinMechanism1, followIndex1);
		File userPic1 = FOLDER.newFile();
		
		// Create user 1.
		FileOutputStream stream = new FileOutputStream(userPic1);
		stream.write("User pic 1\n".getBytes());
		stream.close();
		_createInitialChannel(executor, localActions1, KEY_NAME1, "User 1", "Description 1", userPic1);
		
		// Node 2
		GlobalPinCache pinCache2 = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism2 = new MockPinMechanism(sharedConnection1);
		FollowIndex followIndex2 = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection2 = new MockConnection(KEY_NAME2, PUBLIC_KEY2, pinMechanism2);
		MockLocalActions localActions2 = new MockLocalActions(null, null, sharedConnection2, pinCache2, pinMechanism2, followIndex2);
		File userPic2 = FOLDER.newFile();
		
		// Create user 2.
		stream = new FileOutputStream(userPic2);
		stream.write("User pic 2\n".getBytes());
		stream.close();
		_createInitialChannel(executor, localActions2, KEY_NAME2, "User 2", "Description 2", userPic2);
		
		// User1:  Upload an element.
		String entryName = "entry 1";
		File dataFile = FOLDER.newFile();
		File imageFile = FOLDER.newFile();
		ElementSubCommand[] elements = new ElementSubCommand[] {
				new ElementSubCommand("text/plain", dataFile, null, 0, 0, false),
				new ElementSubCommand("image/jpeg", imageFile, null, 0, 0, true),
		};
		PublishCommand publishCommand = new PublishCommand(entryName, null, elements);
		publishCommand.scheduleActions(executor, localActions1);
		
		// User2:  Follow and verify the data is loaded.
		StartFollowingCommand startFollowingCommand = new StartFollowingCommand(PUBLIC_KEY1);
		startFollowingCommand.scheduleActions(executor, localActions2);
		ListCachedElementsForFolloweeCommand listCommand = new ListCachedElementsForFolloweeCommand(PUBLIC_KEY1);
		listCommand.scheduleActions(executor, localActions2);
	}


	private void _createInitialChannel(Executor executor, MockLocalActions localActions, String keyName, String name, String description, File userPic) throws IOException
	{
		CreateChannelCommand createChannel = new CreateChannelCommand(IPFS_HOST, keyName);
		createChannel.scheduleActions(executor, localActions);
		UpdateDescriptionCommand updateDescription = new UpdateDescriptionCommand(name, description, userPic);
		updateDescription.scheduleActions(executor, localActions);
	}
}
