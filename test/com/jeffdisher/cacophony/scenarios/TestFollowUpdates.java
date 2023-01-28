package com.jeffdisher.cacophony.scenarios;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.ListCachedElementsForFolloweeCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.RefreshFolloweeCommand;
import com.jeffdisher.cacophony.commands.SetGlobalPrefsCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.StopFollowingCommand;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public class TestFollowUpdates
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME1 = "keyName1";
	private static final IpfsKey PUBLIC_KEY1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");
	private static final String KEY_NAME2 = "keyName2";
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo142");

	@Test
	public void testFirstFetchOneElement() throws Throwable
	{
		// Node 1.
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, PUBLIC_KEY1, new MockSingleNode(swarm));
		
		// Create user 1.
		user1.createChannel(KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Node 2
		MockUserNode user2 = new MockUserNode(KEY_NAME2, PUBLIC_KEY2, new MockSingleNode(swarm));
		
		// Create user 2.
		user2.createChannel(KEY_NAME2, "User 2", "Description 2", "User pic 2\n".getBytes());
		
		// User1:  Upload an element.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user1.runCommand(null, publishCommand);
		
		// Verify the data is only in the User1 data store and not yet in User2.
		IpfsFile videoFileHash = MockSingleNode.generateHash(videoFileString.getBytes());
		IpfsFile imageFileHash = MockSingleNode.generateHash(imageFileString.getBytes());
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
		ListCachedElementsForFolloweeCommand listCommand = new ListCachedElementsForFolloweeCommand(PUBLIC_KEY1);
		user2.runCommand(captureStream, listCommand);
		String elementCid = _getFirstElementCid(user1, PUBLIC_KEY1);
		Assert.assertTrue(new String(captureStream.toByteArray()).contains("Element CID: " + elementCid + " (image: " + imageFileHash.toSafeString() + ", leaf: " + videoFileHash.toSafeString() + ")\n"));
		
		// Verify that these elements are now in User2's data store.
		verify = user2.loadDataFromNode(videoFileHash);
		Assert.assertEquals(videoFileString, new String(verify));
		verify = user2.loadDataFromNode(imageFileHash);
		Assert.assertEquals(imageFileString, new String(verify));
		
		// Verify that we fail if we try to run this again.
		try {
			user2.runCommand(null, startFollowingCommand);
			Assert.fail("Exception expected");
		} catch (UsageException e) {
			// Expected.
		}
		user1.shutdown();
		user2.shutdown();
	}

	@Test
	public void testStartStopFollow() throws Throwable
	{
		// Node 1.
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, PUBLIC_KEY1, new MockSingleNode(swarm));
		
		// Create user 1.
		user1.createChannel(KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Node 2
		MockUserNode user2 = new MockUserNode(KEY_NAME2, PUBLIC_KEY2, new MockSingleNode(swarm));
		
		// Create user 2.
		user2.createChannel(KEY_NAME2, "User 2", "Description 2", "User pic 2\n".getBytes());
		
		// User1:  Upload an element.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user1.runCommand(null, publishCommand);
		
		// Verify the data is only in the User1 data store and not yet in User2.
		IpfsFile videoFileHash = MockSingleNode.generateHash(videoFileString.getBytes());
		IpfsFile imageFileHash = MockSingleNode.generateHash(imageFileString.getBytes());
		
		// User2:  Follow and verify the data is loaded.
		StartFollowingCommand startFollowingCommand = new StartFollowingCommand(PUBLIC_KEY1);
		user2.runCommand(null, startFollowingCommand);
		// (capture the output to verify the element is in the list)
		ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
		ListCachedElementsForFolloweeCommand listCommand = new ListCachedElementsForFolloweeCommand(PUBLIC_KEY1);
		user2.runCommand(captureStream, listCommand);
		String elementCid = _getFirstElementCid(user1, PUBLIC_KEY1);
		Assert.assertTrue(new String(captureStream.toByteArray()).contains("Element CID: " + elementCid + " (image: " + imageFileHash.toSafeString() + ", leaf: " + videoFileHash.toSafeString() + ")\n"));
		
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
		
		// Verify that we fail if we try to run this again.
		try {
			user2.runCommand(null, stopFollowingCommand);
			Assert.fail("Exception expected");
		} catch (UsageException e) {
			// Expected.
		}
		user1.shutdown();
		user2.shutdown();
	}

	@Test
	public void testFetchMultipleSizes() throws Throwable
	{
		// Node 1.
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, PUBLIC_KEY1, new MockSingleNode(swarm));
		
		// Create user 1.
		user1.createChannel(KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Node 2
		MockUserNode user2 = new MockUserNode(KEY_NAME2, PUBLIC_KEY2, new MockSingleNode(swarm));
		
		// Create user 2.
		user2.createChannel(KEY_NAME2, "User 2", "Description 2", "User pic 2\n".getBytes());
		
		// Set our preferences for requested video sizes - 640 pixel edge and make sure the data limit is high enough to capture everything.
		user2.runCommand(null, new SetGlobalPrefsCommand(640, 1_000_000L, 0L, 0L));
		
		// Start following before the upload so we can refresh on each update, meaning we will get both with no eviction or change of not caching.
		StartFollowingCommand startFollowingCommand = new StartFollowingCommand(PUBLIC_KEY1);
		user2.runCommand(null, startFollowingCommand);
		
		// Upload 2 elements with multiple sizes.
		// Note that we need to refresh after each one to make sure it actually caches both (otherwise, it will randomly skip older entries to avoid over-filling the cache).
		PublishCommand publishCommand = _createMultiPublishCommand("entry 1", "IMAGE 1\n", new String[] {"VIDEO FILE 640\n", "VIDEO FILE 1024\n"}, new int[] { 640, 1024 } );
		user1.runCommand(null, publishCommand);
		user2.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY1));
		publishCommand = _createMultiPublishCommand("entry 2", "IMAGE 2\n", new String[] {"VIDEO FILE 320\n", "VIDEO FILE next 640\n"}, new int[] { 320, 640 } );
		user1.runCommand(null, publishCommand);
		user2.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY1));
		
		// User2:  Follow and verify the data is loaded.
		// (capture the output to verify the element is in the list)
		ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
		ListCachedElementsForFolloweeCommand listCommand = new ListCachedElementsForFolloweeCommand(PUBLIC_KEY1);
		user2.runCommand(captureStream, listCommand);
		String capturedString = new String(captureStream.toByteArray());
		
		IpfsFile image1FileHash = MockSingleNode.generateHash("IMAGE 1\n".getBytes());
		IpfsFile video1FileHash = MockSingleNode.generateHash("VIDEO FILE 640\n".getBytes());
		IpfsFile image2FileHash = MockSingleNode.generateHash("IMAGE 2\n".getBytes());
		IpfsFile video2FileHash = MockSingleNode.generateHash("VIDEO FILE next 640\n".getBytes());
		
		Assert.assertTrue(capturedString.contains("(image: " + image1FileHash.toSafeString() + ", leaf: " + video1FileHash.toSafeString() + ")\n"));
		Assert.assertTrue(capturedString.contains("(image: " + image2FileHash.toSafeString() + ", leaf: " + video2FileHash.toSafeString() + ")\n"));
		
		// Verify that the other sizes are in user1's store but not user2's.
		IpfsFile missingVideo1Hash = MockSingleNode.generateHash("VIDEO FILE 1024\n".getBytes());
		IpfsFile missingVideo2Hash = MockSingleNode.generateHash("VIDEO FILE 320\n".getBytes());
		Assert.assertNotNull(user1.loadDataFromNode(missingVideo1Hash));
		Assert.assertNotNull(user1.loadDataFromNode(missingVideo2Hash));
		Assert.assertNull(user2.loadDataFromNode(missingVideo1Hash));
		Assert.assertNull(user2.loadDataFromNode(missingVideo2Hash));
		user1.shutdown();
		user2.shutdown();
	}


	private static String _getFirstElementCid(MockUserNode userNode, IpfsKey publicKey) throws IpfsConnectionException, FailedDeserializationException
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
		return new PublishCommand(entryName, "description", null, elements);
	}

	private static PublishCommand _createMultiPublishCommand(String entryName, String imageFileString, String[] videoFileStrings, int heights[]) throws IOException
	{
		Assert.assertEquals(videoFileStrings.length, heights.length);
		File imageFile = FOLDER.newFile();
		FileOutputStream imageStream = new FileOutputStream(imageFile);
		imageStream.write(imageFileString.getBytes());
		imageStream.close();
		
		File[] dataFiles = new File[videoFileStrings.length];
		for (int i = 0; i < dataFiles.length; ++i)
		{
			dataFiles[i] = FOLDER.newFile();
			FileOutputStream dataStream = new FileOutputStream(dataFiles[i]);
			dataStream.write(videoFileStrings[i].getBytes());
			dataStream.close();
		}
		
		ElementSubCommand[] elements = new ElementSubCommand[videoFileStrings.length + 1];
		elements[0] = new ElementSubCommand("image/jpeg", imageFile, 0, 0, true);
		for (int i = 0; i < dataFiles.length; ++i)
		{
			int width = heights[i] / 2;
			elements[i + 1] = new ElementSubCommand("video/mp4", dataFiles[i], heights[i], width, false);
		};
		return new PublishCommand(entryName, "description", null, elements);
	}
}
