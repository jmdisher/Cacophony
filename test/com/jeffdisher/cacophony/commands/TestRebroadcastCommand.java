package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;


public class TestRebroadcastCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME = "keyName";
	private static final IpfsFile MISC_FILE = MockSingleNode.generateHash(new byte[] {1});


	@Test
	public void testRebroadcastFromFollowee() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user2 = new MockUserNode(KEY_NAME, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// We need to add the followees.
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(MockKeys.K2));
		
		// Publish something we can copy.
		File fakeVideo = FOLDER.newFile();
		Files.write(fakeVideo.toPath(), "video".getBytes());
		File fakeImage = FOLDER.newFile();
		Files.write(fakeImage.toPath(), "image".getBytes());
		user2.runCommand(null, new PublishCommand("entry 1", "", null, null, new ElementSubCommand[] {
				new ElementSubCommand("video/webm", fakeVideo, 720, 1280, false) ,
				new ElementSubCommand("image/jpeg", fakeImage, 720, 1280, true) ,
		}));
		user.runCommand(null, new RefreshNextFolloweeCommand());
		
		// Verify that our record list is empty.
		AbstractIndex index = AbstractIndex.DESERIALIZER.apply(user.loadDataFromNode(user.resolveKeyOnNode(MockKeys.K1)));
		AbstractRecords records = AbstractRecords.DESERIALIZER.apply(user.loadDataFromNode(index.recordsCid));
		Assert.assertEquals(0, records.getRecordList().size());
		
		// Now, rebroadcast this and verify that the new element is in our list.
		index = AbstractIndex.DESERIALIZER.apply(user.loadDataFromNode(user.resolveKeyOnNode(MockKeys.K2)));
		records = AbstractRecords.DESERIALIZER.apply(user.loadDataFromNode(index.recordsCid));
		IpfsFile recordToRebroadcast = records.getRecordList().get(0);
		user.runCommand(null, new RebroadcastCommand(recordToRebroadcast));
		
		// Verify that our record list now contains this.
		index = AbstractIndex.DESERIALIZER.apply(user.loadDataFromNode(user.resolveKeyOnNode(MockKeys.K1)));
		records = AbstractRecords.DESERIALIZER.apply(user.loadDataFromNode(index.recordsCid));
		Assert.assertEquals(recordToRebroadcast, records.getRecordList().get(0));
		
		user2.shutdown();
		user.shutdown();
	}

	@Test
	public void testRebroadcastUnknown() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user2 = new MockUserNode(KEY_NAME, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Publish something we can copy.
		File fakeVideo = FOLDER.newFile();
		Files.write(fakeVideo.toPath(), "video".getBytes());
		File fakeImage = FOLDER.newFile();
		Files.write(fakeImage.toPath(), "image".getBytes());
		user2.runCommand(null, new PublishCommand("entry 1", "", null, null, new ElementSubCommand[] {
				new ElementSubCommand("video/webm", fakeVideo, 720, 1280, false) ,
				new ElementSubCommand("image/jpeg", fakeImage, 720, 1280, true) ,
		}));
		
		// Verify that our record list is empty.
		AbstractIndex index = AbstractIndex.DESERIALIZER.apply(user.loadDataFromNode(user.resolveKeyOnNode(MockKeys.K1)));
		AbstractRecords records = AbstractRecords.DESERIALIZER.apply(user.loadDataFromNode(index.recordsCid));
		Assert.assertEquals(0, records.getRecordList().size());
		
		// Now, rebroadcast this and verify that the new element is in our list.
		index = AbstractIndex.DESERIALIZER.apply(user2.loadDataFromNode(user2.resolveKeyOnNode(MockKeys.K2)));
		records = AbstractRecords.DESERIALIZER.apply(user2.loadDataFromNode(index.recordsCid));
		IpfsFile recordToRebroadcast = records.getRecordList().get(0);
		user.runCommand(null, new RebroadcastCommand(recordToRebroadcast));
		
		// Verify that our record list now contains this.
		index = AbstractIndex.DESERIALIZER.apply(user.loadDataFromNode(user.resolveKeyOnNode(MockKeys.K1)));
		records = AbstractRecords.DESERIALIZER.apply(user.loadDataFromNode(index.recordsCid));
		Assert.assertEquals(recordToRebroadcast, records.getRecordList().get(0));
		
		user2.shutdown();
		user.shutdown();
	}

	@Test
	public void testRebroadcastOurDuplicate() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// Create the channel and publish an entry.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		File fakeVideo = FOLDER.newFile();
		Files.write(fakeVideo.toPath(), "video".getBytes());
		File fakeImage = FOLDER.newFile();
		Files.write(fakeImage.toPath(), "image".getBytes());
		user.runCommand(null, new PublishCommand("entry 1", "", null, null, new ElementSubCommand[] {
				new ElementSubCommand("video/webm", fakeVideo, 720, 1280, false) ,
				new ElementSubCommand("image/jpeg", fakeImage, 720, 1280, true) ,
		}));
		
		// Now, rebroadcast this and verify it is a failure.
		IpfsFile initialRoot = user.resolveKeyOnNode(MockKeys.K1);
		AbstractIndex index = AbstractIndex.DESERIALIZER.apply(user.loadDataFromNode(initialRoot));
		AbstractRecords records = AbstractRecords.DESERIALIZER.apply(user.loadDataFromNode(index.recordsCid));
		Assert.assertEquals(1, records.getRecordList().size());
		IpfsFile recordToRebroadcast = records.getRecordList().get(0);
		boolean didFail = false;
		try
		{
			user.runCommand(null, new RebroadcastCommand(recordToRebroadcast));
		}
		catch (UsageException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		// Verify that our record list is unchanged.
		Assert.assertEquals(initialRoot, user.resolveKeyOnNode(MockKeys.K1));
		
		user.shutdown();
	}

	@Test
	public void testRebroadcastBrokenElement() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user2 = new MockUserNode(KEY_NAME, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Publish something we can copy.
		File fakeVideo = FOLDER.newFile();
		Files.write(fakeVideo.toPath(), "video".getBytes());
		File fakeImage = FOLDER.newFile();
		Files.write(fakeImage.toPath(), "image".getBytes());
		user2.runCommand(null, new PublishCommand("entry 1", "", null, null, new ElementSubCommand[] {
				new ElementSubCommand("video/webm", fakeVideo, 720, 1280, false) ,
				new ElementSubCommand("image/jpeg", fakeImage, 720, 1280, true) ,
		}));
		
		// Verify that our record list is empty.
		IpfsFile initialRoot = user.resolveKeyOnNode(MockKeys.K1);
		AbstractIndex index = AbstractIndex.DESERIALIZER.apply(user.loadDataFromNode(initialRoot));
		AbstractRecords records = AbstractRecords.DESERIALIZER.apply(user.loadDataFromNode(index.recordsCid));
		Assert.assertEquals(0, records.getRecordList().size());
		
		// Now, rebroadcast this and verify that the new element is in our list.
		index = AbstractIndex.DESERIALIZER.apply(user2.loadDataFromNode(user2.resolveKeyOnNode(MockKeys.K2)));
		records = AbstractRecords.DESERIALIZER.apply(user2.loadDataFromNode(index.recordsCid));
		IpfsFile recordToRebroadcast = records.getRecordList().get(0);
		AbstractRecord recordToExamine = AbstractRecord.DESERIALIZER.apply(user2.loadDataFromNode(recordToRebroadcast));
		Assert.assertEquals(1, recordToExamine.getVideoExtension().size());
		IpfsFile elementToDelete = recordToExamine.getVideoExtension().get(0).cid();
		user2.deleteFile(elementToDelete);
		
		// We should see an IPFS connection exception since we will timeout looking for an element.
		boolean didFail = false;
		try
		{
			user.runCommand(null, new RebroadcastCommand(recordToRebroadcast));
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		// Verify that we didn't change anything, as the rebroadcast would fail.
		Assert.assertEquals(initialRoot, user.resolveKeyOnNode(MockKeys.K1));
		Assert.assertNull(user.loadDataFromNode(recordToRebroadcast));
		Assert.assertNull(user.loadDataFromNode(recordToExamine.getThumbnailCid()));
		
		user2.shutdown();
		user.shutdown();
	}

	@Test
	public void testMissingChannel() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		RebroadcastCommand command = new RebroadcastCommand(MISC_FILE);
		try
		{
			user1.runCommand(null, command);
			Assert.fail();
		}
		catch (UsageException e)
		{
			// Expected.
		}
	}
}
