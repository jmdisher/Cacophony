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

import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.ListCachedElementsForFolloweeCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.StopFollowingCommand;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.testutils.MockConnection;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestFollowUpdates
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME1 = "keyName1";
	private static final IpfsKey PUBLIC_KEY1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");
	private static final String KEY_NAME2 = "keyName2";
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo142");

	@Test
	public void testFirstFetchOneElement() throws IOException
	{
		// Node 1.
		MockUserNode user1 = new MockUserNode(KEY_NAME1, PUBLIC_KEY1, null);
		
		// Create user 1.
		user1.createChannel(KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Node 2
		MockUserNode user2 = new MockUserNode(KEY_NAME2, PUBLIC_KEY2, user1);
		
		// Create user 2.
		user2.createChannel(KEY_NAME2, "User 2", "Description 2", "User pic 2\n".getBytes());
		
		// User1:  Upload an element.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user1.runCommand(null, publishCommand);
		
		// Verify the data is only in the User1 data store and not yet in User2.
		IpfsFile videoFileHash = MockConnection.generateHash(videoFileString.getBytes());
		IpfsFile imageFileHash = MockConnection.generateHash(imageFileString.getBytes());
		byte[] verify = user1.loadDataFromNode(videoFileHash);
		Assert.assertEquals(videoFileString, new String(verify));
		verify = user1.loadDataFromNode(imageFileHash);
		Assert.assertEquals(imageFileString, new String(verify));
		Assert.assertNull(user2.loadDataFromNode(videoFileHash));
		Assert.assertNull(user2.loadDataFromNode(imageFileHash));
		
		// User2:  Follow and verify the data is loaded.
		StartFollowingCommand startFollowingCommand = new StartFollowingCommand(PUBLIC_KEY1);
		user2.runCommand(null, startFollowingCommand);
		// (capture the output to verify the element is in the list)
		ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
		Executor executor = new Executor(new PrintStream(captureStream));
		ListCachedElementsForFolloweeCommand listCommand = new ListCachedElementsForFolloweeCommand(PUBLIC_KEY1);
		user2.runCommand(executor, listCommand);
		String elementCid = _getFirstElementCid(user1, PUBLIC_KEY1);
		Assert.assertTrue(new String(captureStream.toByteArray()).contains("Element CID: " + elementCid + " (image: " + imageFileHash.toSafeString() + ", leaf: " + videoFileHash.toSafeString() + ")\n"));
		
		// Verify that these elements are now in User2's data store.
		verify = user2.loadDataFromNode(videoFileHash);
		Assert.assertEquals(videoFileString, new String(verify));
		verify = user2.loadDataFromNode(imageFileHash);
		Assert.assertEquals(imageFileString, new String(verify));
	}

	@Test
	public void testStartStopFollow() throws IOException
	{
		// Node 1.
		MockUserNode user1 = new MockUserNode(KEY_NAME1, PUBLIC_KEY1, null);
		
		// Create user 1.
		user1.createChannel(KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Node 2
		MockUserNode user2 = new MockUserNode(KEY_NAME2, PUBLIC_KEY2, user1);
		
		// Create user 2.
		user2.createChannel(KEY_NAME2, "User 2", "Description 2", "User pic 2\n".getBytes());
		
		// User1:  Upload an element.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user1.runCommand(null, publishCommand);
		
		// Verify the data is only in the User1 data store and not yet in User2.
		IpfsFile videoFileHash = MockConnection.generateHash(videoFileString.getBytes());
		IpfsFile imageFileHash = MockConnection.generateHash(imageFileString.getBytes());
		
		// User2:  Follow and verify the data is loaded.
		StartFollowingCommand startFollowingCommand = new StartFollowingCommand(PUBLIC_KEY1);
		user2.runCommand(null, startFollowingCommand);
		// (capture the output to verify the element is in the list)
		ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
		Executor executor = new Executor(new PrintStream(captureStream));
		ListCachedElementsForFolloweeCommand listCommand = new ListCachedElementsForFolloweeCommand(PUBLIC_KEY1);
		user2.runCommand(executor, listCommand);
		String elementCid = _getFirstElementCid(user1, PUBLIC_KEY1);
		Assert.assertTrue(new String(captureStream.toByteArray()).contains("Element CID: " + elementCid + " (image: " + imageFileHash.toSafeString() + ", leaf: " + videoFileHash.toSafeString() + ")\n"));
		executor = null;
		
		// Verify that these elements are now in User2's data store.
		byte[] verify = user2.loadDataFromNode(videoFileHash);
		Assert.assertEquals(videoFileString, new String(verify));
		verify = user2.loadDataFromNode(imageFileHash);
		Assert.assertEquals(imageFileString, new String(verify));
		
		// Now, stop following them and verify that all of this data has been removed from the local store but is still on the remote store.
		StopFollowingCommand stopFollowingCommand = new StopFollowingCommand(PUBLIC_KEY1);
		user2.runCommand(null, stopFollowingCommand);
		verify = user1.loadDataFromNode(videoFileHash);
		Assert.assertEquals(videoFileString, new String(verify));
		verify = user1.loadDataFromNode(imageFileHash);
		Assert.assertEquals(imageFileString, new String(verify));
		Assert.assertNull(user2.loadDataFromNode(videoFileHash));
		Assert.assertNull(user2.loadDataFromNode(imageFileHash));
	}


	private static String _getFirstElementCid(MockUserNode userNode, IpfsKey publicKey) throws IOException
	{
		StreamIndex index = GlobalData.deserializeIndex(userNode.loadDataFromNode(userNode.resolveKeyOnNode(PUBLIC_KEY1)));
		StreamRecords records = GlobalData.deserializeRecords(userNode.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
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
