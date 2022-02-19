package com.jeffdisher.cacophony.scenarios;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.AddRecommendationCommand;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ListRecommendationsCommand;
import com.jeffdisher.cacophony.commands.MockConnection;
import com.jeffdisher.cacophony.commands.MockLocalActions;
import com.jeffdisher.cacophony.commands.MockPinMechanism;
import com.jeffdisher.cacophony.commands.RemoveRecommendationCommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestRecommendations
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME1 = "keyName1";
	private static final IpfsKey PUBLIC_KEY1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo142");

	@Test
	public void testAddRecommendation() throws IOException
	{
		Executor executor = new Executor(System.out);
		
		// We only need a single node.
		GlobalPinCache pinCache1 = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism1 = new MockPinMechanism(null);
		FollowIndex followIndex1 = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection1 = new MockConnection(KEY_NAME1, PUBLIC_KEY1, pinMechanism1, null);
		MockLocalActions localActions1 = new MockLocalActions(null, null, sharedConnection1, pinCache1, pinMechanism1, followIndex1);
		
		// We only need a single real user.
		_createInitialChannel(executor, localActions1, KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Add the recommendation.
		AddRecommendationCommand addCommand = new AddRecommendationCommand(PUBLIC_KEY2);
		addCommand.scheduleActions(executor, localActions1);
		
		// List the recommendations - make sure we find the key.
		ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
		executor = new Executor(new PrintStream(captureStream));
		ListRecommendationsCommand listCommand = new ListRecommendationsCommand();
		listCommand.scheduleActions(executor, localActions1);
		Assert.assertTrue(new String(captureStream.toByteArray()).equals("Recommending: " + PUBLIC_KEY2.toPublicKey() + "\n"));
		
		// Remove the recommendation.
		executor = new Executor(System.out);
		RemoveRecommendationCommand removeCommand = new RemoveRecommendationCommand(PUBLIC_KEY2);
		removeCommand.scheduleActions(executor, localActions1);
		
		// List the recommendations - make sure we see an empty list.
		captureStream = new ByteArrayOutputStream();
		executor = new Executor(new PrintStream(captureStream));
		listCommand = new ListRecommendationsCommand();
		listCommand.scheduleActions(executor, localActions1);
		Assert.assertTrue(new String(captureStream.toByteArray()).equals(""));
	}


	private void _createInitialChannel(Executor executor, MockLocalActions localActions, String keyName, String name, String description, byte[] userPicData) throws IOException
	{
		File userPic = FOLDER.newFile();
		FileOutputStream stream = new FileOutputStream(userPic);
		stream.write(userPicData);
		stream.close();
		
		CreateChannelCommand createChannel = new CreateChannelCommand(IPFS_HOST, keyName);
		createChannel.scheduleActions(executor, localActions);
		UpdateDescriptionCommand updateDescription = new UpdateDescriptionCommand(name, description, userPic);
		updateDescription.scheduleActions(executor, localActions);
	}
}
