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

import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.ListCachedElementsForFolloweeCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.StopFollowingCommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.testutils.MockConnection;
import com.jeffdisher.cacophony.testutils.MockLocalActions;
import com.jeffdisher.cacophony.testutils.MockPinMechanism;
import com.jeffdisher.cacophony.types.IpfsFile;
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
		Executor executor = new Executor(System.out);
		
		// Node 1.
		GlobalPinCache pinCache1 = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism1 = new MockPinMechanism(null);
		FollowIndex followIndex1 = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection1 = new MockConnection(KEY_NAME1, PUBLIC_KEY1, pinMechanism1, null);
		MockLocalActions localActions1 = new MockLocalActions(null, null, sharedConnection1, pinCache1, pinMechanism1, followIndex1);
		
		// Create user 1.
		_createInitialChannel(executor, localActions1, KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Node 2
		GlobalPinCache pinCache2 = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism2 = new MockPinMechanism(sharedConnection1);
		FollowIndex followIndex2 = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection2 = new MockConnection(KEY_NAME2, PUBLIC_KEY2, pinMechanism2, sharedConnection1);
		MockLocalActions localActions2 = new MockLocalActions(null, null, sharedConnection2, pinCache2, pinMechanism2, followIndex2);
		
		// Create user 2.
		_createInitialChannel(executor, localActions2, KEY_NAME2, "User 2", "Description 2", "User pic 2\n".getBytes());
		
		// User1:  Upload an element.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		publishCommand.scheduleActions(executor, localActions1);
		
		// Verify the data is only in the User1 data store and not yet in User2.
		IpfsFile videoFileHash = MockConnection.generateHash(videoFileString.getBytes());
		IpfsFile imageFileHash = MockConnection.generateHash(imageFileString.getBytes());
		byte[] verify = sharedConnection1.loadData(videoFileHash);
		Assert.assertEquals(videoFileString, new String(verify));
		verify = sharedConnection1.loadData(imageFileHash);
		Assert.assertEquals(imageFileString, new String(verify));
		Assert.assertNull(sharedConnection2.loadData(videoFileHash));
		Assert.assertNull(sharedConnection2.loadData(imageFileHash));
		
		// User2:  Follow and verify the data is loaded.
		StartFollowingCommand startFollowingCommand = new StartFollowingCommand(PUBLIC_KEY1);
		startFollowingCommand.scheduleActions(executor, localActions2);
		// (capture the output to verify the element is in the list)
		ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
		executor = new Executor(new PrintStream(captureStream));
		ListCachedElementsForFolloweeCommand listCommand = new ListCachedElementsForFolloweeCommand(PUBLIC_KEY1);
		listCommand.scheduleActions(executor, localActions2);
		String elementCid = _getFirstElementCid(sharedConnection1, PUBLIC_KEY1);
		Assert.assertTrue(new String(captureStream.toByteArray()).contains("Element CID: " + elementCid + " (image: " + imageFileHash.toSafeString() + ", leaf: " + videoFileHash.toSafeString() + ")\n"));
		
		// Verify that these elements are now in User2's data store.
		verify = sharedConnection2.loadData(videoFileHash);
		Assert.assertEquals(videoFileString, new String(verify));
		verify = sharedConnection2.loadData(imageFileHash);
		Assert.assertEquals(imageFileString, new String(verify));
	}

	@Test
	public void testStartStopFollow() throws IOException
	{
		Executor executor = new Executor(System.out);
		
		// Node 1.
		GlobalPinCache pinCache1 = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism1 = new MockPinMechanism(null);
		FollowIndex followIndex1 = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection1 = new MockConnection(KEY_NAME1, PUBLIC_KEY1, pinMechanism1, null);
		MockLocalActions localActions1 = new MockLocalActions(null, null, sharedConnection1, pinCache1, pinMechanism1, followIndex1);
		
		// Create user 1.
		_createInitialChannel(executor, localActions1, KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Node 2
		GlobalPinCache pinCache2 = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism2 = new MockPinMechanism(sharedConnection1);
		FollowIndex followIndex2 = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection2 = new MockConnection(KEY_NAME2, PUBLIC_KEY2, pinMechanism2, sharedConnection1);
		MockLocalActions localActions2 = new MockLocalActions(null, null, sharedConnection2, pinCache2, pinMechanism2, followIndex2);
		
		// Create user 2.
		_createInitialChannel(executor, localActions2, KEY_NAME2, "User 2", "Description 2", "User pic 2\n".getBytes());
		
		// User1:  Upload an element.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		publishCommand.scheduleActions(executor, localActions1);
		
		// Verify the data is only in the User1 data store and not yet in User2.
		IpfsFile videoFileHash = MockConnection.generateHash(videoFileString.getBytes());
		IpfsFile imageFileHash = MockConnection.generateHash(imageFileString.getBytes());
		
		// User2:  Follow and verify the data is loaded.
		StartFollowingCommand startFollowingCommand = new StartFollowingCommand(PUBLIC_KEY1);
		startFollowingCommand.scheduleActions(executor, localActions2);
		// (capture the output to verify the element is in the list)
		ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
		executor = new Executor(new PrintStream(captureStream));
		ListCachedElementsForFolloweeCommand listCommand = new ListCachedElementsForFolloweeCommand(PUBLIC_KEY1);
		listCommand.scheduleActions(executor, localActions2);
		String elementCid = _getFirstElementCid(sharedConnection1, PUBLIC_KEY1);
		Assert.assertTrue(new String(captureStream.toByteArray()).contains("Element CID: " + elementCid + " (image: " + imageFileHash.toSafeString() + ", leaf: " + videoFileHash.toSafeString() + ")\n"));
		
		// Verify that these elements are now in User2's data store.
		byte[] verify = sharedConnection2.loadData(videoFileHash);
		Assert.assertEquals(videoFileString, new String(verify));
		verify = sharedConnection2.loadData(imageFileHash);
		Assert.assertEquals(imageFileString, new String(verify));
		
		// Now, stop following them and verify that all of this data has been removed from the local store but is still on the remote store.
		StopFollowingCommand stopFollowingCommand = new StopFollowingCommand(PUBLIC_KEY1);
		stopFollowingCommand.scheduleActions(executor, localActions2);
		verify = sharedConnection1.loadData(videoFileHash);
		Assert.assertEquals(videoFileString, new String(verify));
		verify = sharedConnection1.loadData(imageFileHash);
		Assert.assertEquals(imageFileString, new String(verify));
		Assert.assertNull(sharedConnection2.loadData(videoFileHash));
		Assert.assertNull(sharedConnection2.loadData(imageFileHash));
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

	private static String _getFirstElementCid(MockConnection store, IpfsKey publicKey) throws IOException
	{
		StreamIndex index = GlobalData.deserializeIndex(store.loadData(store.resolve(PUBLIC_KEY1)));
		StreamRecords records = GlobalData.deserializeRecords(store.loadData(IpfsFile.fromIpfsCid(index.getRecords())));
		return records.getRecord().get(0);
	}

	private static PublishCommand _createPublishCommand(String entryName, String imageFileString, String videoFileString) throws IOException
	{
		File dataFile = FOLDER.newFile();
		FileOutputStream dataStream = new FileOutputStream(dataFile);
		dataStream.write(videoFileString.getBytes());
		dataStream.close();
		File imageFile = FOLDER.newFile();
		FileOutputStream imageStream = new FileOutputStream(imageFile);
		imageStream.write(imageFileString.getBytes());
		imageStream.close();
		ElementSubCommand[] elements = new ElementSubCommand[] {
				new ElementSubCommand("video/mp4", dataFile, 720, 1280, false),
				new ElementSubCommand("image/jpeg", imageFile, 0, 0, true),
		};
		return new PublishCommand(entryName, null, elements);
	}
}
