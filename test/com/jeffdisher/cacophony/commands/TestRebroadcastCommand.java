package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public class TestRebroadcastCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");
	private static final IpfsFile MISC_FILE = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");


	@Test
	public void testRebroadcastFromFollowee() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, new MockSingleNode(swarm));
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(swarm));
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// We need to add the followees.
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		
		// Publish something we can copy.
		File fakeVideo = FOLDER.newFile();
		Files.write(fakeVideo.toPath(), "video".getBytes());
		File fakeImage = FOLDER.newFile();
		Files.write(fakeImage.toPath(), "image".getBytes());
		user2.runCommand(null, new PublishCommand("entry 1", "", null, new ElementSubCommand[] {
				new ElementSubCommand("video/webm", fakeVideo, 720, 1280, false) ,
				new ElementSubCommand("image/jpeg", fakeImage, 720, 1280, true) ,
		}));
		user.runCommand(null, new RefreshNextFolloweeCommand());
		
		// Verify that our record list is empty.
		StreamIndex index = GlobalData.deserializeIndex(user.loadDataFromNode(user.resolveKeyOnNode(PUBLIC_KEY)));
		StreamRecords records = GlobalData.deserializeRecords(user.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		Assert.assertEquals(0, records.getRecord().size());
		
		// Now, rebroadcast this and verify that the new element is in our list.
		index = GlobalData.deserializeIndex(user.loadDataFromNode(user.resolveKeyOnNode(PUBLIC_KEY2)));
		records = GlobalData.deserializeRecords(user.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		IpfsFile recordToRebroadcast = IpfsFile.fromIpfsCid(records.getRecord().get(0));
		user.runCommand(null, new RebroadcastCommand(recordToRebroadcast));
		
		// Verify that our record list now contains this.
		index = GlobalData.deserializeIndex(user.loadDataFromNode(user.resolveKeyOnNode(PUBLIC_KEY)));
		records = GlobalData.deserializeRecords(user.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		Assert.assertEquals(recordToRebroadcast.toSafeString(), records.getRecord().get(0));
		
		user2.shutdown();
		user.shutdown();
	}

	@Test
	public void testRebroadcastUnknown() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, new MockSingleNode(swarm));
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(swarm));
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Publish something we can copy.
		File fakeVideo = FOLDER.newFile();
		Files.write(fakeVideo.toPath(), "video".getBytes());
		File fakeImage = FOLDER.newFile();
		Files.write(fakeImage.toPath(), "image".getBytes());
		user2.runCommand(null, new PublishCommand("entry 1", "", null, new ElementSubCommand[] {
				new ElementSubCommand("video/webm", fakeVideo, 720, 1280, false) ,
				new ElementSubCommand("image/jpeg", fakeImage, 720, 1280, true) ,
		}));
		
		// Verify that our record list is empty.
		StreamIndex index = GlobalData.deserializeIndex(user.loadDataFromNode(user.resolveKeyOnNode(PUBLIC_KEY)));
		StreamRecords records = GlobalData.deserializeRecords(user.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		Assert.assertEquals(0, records.getRecord().size());
		
		// Now, rebroadcast this and verify that the new element is in our list.
		index = GlobalData.deserializeIndex(user2.loadDataFromNode(user2.resolveKeyOnNode(PUBLIC_KEY2)));
		records = GlobalData.deserializeRecords(user2.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		IpfsFile recordToRebroadcast = IpfsFile.fromIpfsCid(records.getRecord().get(0));
		user.runCommand(null, new RebroadcastCommand(recordToRebroadcast));
		
		// Verify that our record list now contains this.
		index = GlobalData.deserializeIndex(user.loadDataFromNode(user.resolveKeyOnNode(PUBLIC_KEY)));
		records = GlobalData.deserializeRecords(user.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		Assert.assertEquals(recordToRebroadcast.toSafeString(), records.getRecord().get(0));
		
		user2.shutdown();
		user.shutdown();
	}

	@Test
	public void testRebroadcastOurDuplicate() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(swarm));
		
		// Create the channel and publish an entry.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		File fakeVideo = FOLDER.newFile();
		Files.write(fakeVideo.toPath(), "video".getBytes());
		File fakeImage = FOLDER.newFile();
		Files.write(fakeImage.toPath(), "image".getBytes());
		user.runCommand(null, new PublishCommand("entry 1", "", null, new ElementSubCommand[] {
				new ElementSubCommand("video/webm", fakeVideo, 720, 1280, false) ,
				new ElementSubCommand("image/jpeg", fakeImage, 720, 1280, true) ,
		}));
		
		// Now, rebroadcast this and verify it is a failure.
		IpfsFile initialRoot = user.resolveKeyOnNode(PUBLIC_KEY);
		StreamIndex index = GlobalData.deserializeIndex(user.loadDataFromNode(initialRoot));
		StreamRecords records = GlobalData.deserializeRecords(user.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		Assert.assertEquals(1, records.getRecord().size());
		IpfsFile recordToRebroadcast = IpfsFile.fromIpfsCid(records.getRecord().get(0));
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
		Assert.assertEquals(initialRoot, user.resolveKeyOnNode(PUBLIC_KEY));
		
		user.shutdown();
	}

	@Test
	public void testRebroadcastBrokenElement() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, new MockSingleNode(swarm));
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(swarm));
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Publish something we can copy.
		File fakeVideo = FOLDER.newFile();
		Files.write(fakeVideo.toPath(), "video".getBytes());
		File fakeImage = FOLDER.newFile();
		Files.write(fakeImage.toPath(), "image".getBytes());
		user2.runCommand(null, new PublishCommand("entry 1", "", null, new ElementSubCommand[] {
				new ElementSubCommand("video/webm", fakeVideo, 720, 1280, false) ,
				new ElementSubCommand("image/jpeg", fakeImage, 720, 1280, true) ,
		}));
		
		// Verify that our record list is empty.
		IpfsFile initialRoot = user.resolveKeyOnNode(PUBLIC_KEY);
		StreamIndex index = GlobalData.deserializeIndex(user.loadDataFromNode(initialRoot));
		StreamRecords records = GlobalData.deserializeRecords(user.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		Assert.assertEquals(0, records.getRecord().size());
		
		// Now, rebroadcast this and verify that the new element is in our list.
		index = GlobalData.deserializeIndex(user2.loadDataFromNode(user2.resolveKeyOnNode(PUBLIC_KEY2)));
		records = GlobalData.deserializeRecords(user2.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		IpfsFile recordToRebroadcast = IpfsFile.fromIpfsCid(records.getRecord().get(0));
		StreamRecord recordToExamine = GlobalData.deserializeRecord(user2.loadDataFromNode(recordToRebroadcast));
		IpfsFile elementToDelete = IpfsFile.fromIpfsCid(recordToExamine.getElements().getElement().get(0).getCid());
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
		Assert.assertEquals(initialRoot, user.resolveKeyOnNode(PUBLIC_KEY));
		Assert.assertNull(user.loadDataFromNode(recordToRebroadcast));
		Assert.assertNull(user.loadDataFromNode(IpfsFile.fromIpfsCid(recordToExamine.getElements().getElement().get(1).getCid())));
		
		user2.shutdown();
		user.shutdown();
	}

	@Test
	public void testMissingChannel() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(new MockSwarm()));
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
