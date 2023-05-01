package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.UsageException;


public class TestRefreshNextFolloweeCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");
	private static final IpfsKey PUBLIC_KEY3 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo145");

	@Test
	public void testNoFolloweesException() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		boolean didThrow = false;
		try
		{
			user.runCommand(null, command);
		}
		catch (UsageException e)
		{
			didThrow = true;
		}
		Assert.assertTrue(didThrow);
		user.shutdown();
	}

	@Test
	public void testOneFollowee() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// We need to add a followee.
		user1.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		user.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY2));
		
		// We should be able to run this multiple times, without it causing problems.
		user.runCommand(null, command);
		user.runCommand(null, command);
		user1.shutdown();
		user.shutdown();
	}

	@Test
	public void testTwoFollowees() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockSwarm swarm = new MockSwarm();
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user3 = new MockUserNode(KEY_NAME, PUBLIC_KEY3, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// We need to add the followees.
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		user.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY2));
		user3.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY3));
		user.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY3));
		
		// We should be able to run this multiple times, without it causing problems.
		IFolloweeReading followees = user.readFollowIndex();
		IpfsKey nextKey = followees.getNextFolloweeToPoll();
		// We expect to do the initial check on the first one we added (since it was populated when first read).
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		user.runCommand(null, command);
		nextKey = user.readFollowIndex().getNextFolloweeToPoll();
		// The key should have rotated, even though nothing changed.
		Assert.assertEquals(PUBLIC_KEY3, nextKey);
		user.runCommand(null, command);
		nextKey = followees.getNextFolloweeToPoll();
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		user2.shutdown();
		user3.shutdown();
		user.shutdown();
	}

	@Test
	public void testTwoFolloweesWithBrokenRecordsList() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockSwarm swarm = new MockSwarm();
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user3 = new MockUserNode(KEY_NAME, PUBLIC_KEY3, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// We need to add the followees.
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		user.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY2));
		user3.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY3));
		user.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY3));
		
		// We should be able to run this multiple times, without it causing problems.
		IFolloweeReading followees = user.readFollowIndex();
		IpfsKey nextKey = followees.getNextFolloweeToPoll();
		// We expect to do the initial check on the first one we added (since it was populated when first read).
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		user.runCommand(null, command);
		nextKey = user.readFollowIndex().getNextFolloweeToPoll();
		// The key should have rotated, even though nothing changed.
		Assert.assertEquals(PUBLIC_KEY3, nextKey);
		
		// Update 2 data elements and remove one of them from the node before refreshing this user.
		File tempFile = FOLDER.newFile();
		FileOutputStream stream = new FileOutputStream(tempFile);
		stream.write("file".getBytes());
		stream.close();
		user3.runCommand(null, new PublishCommand("entry 1", "", null, new ElementSubCommand[] {
				new ElementSubCommand("text/plain", tempFile, 0, 0, false) ,
		}));
		user3.runCommand(null, new PublishCommand("entry 2", "", null, new ElementSubCommand[] {}));
		StreamIndex index = GlobalData.deserializeIndex(user3.loadDataFromNode(user3.resolveKeyOnNode(PUBLIC_KEY3)));
		IpfsFile metaDataToDelete = IpfsFile.fromIpfsCid(index.getRecords());
		user3.deleteFile(metaDataToDelete);
		
		// Note that we expect this to fail.  Verify we see the exception, observe the root didn't change, and the next to poll advanced.
		followees = user.readFollowIndex();
		IpfsKey beforeToPoll = followees.getNextFolloweeToPoll();
		Assert.assertEquals(PUBLIC_KEY3, beforeToPoll);
		IpfsFile beforeRoot = followees.getLastFetchedRootForFollowee(beforeToPoll);
		boolean didFail = false;
		try
		{
			user.runCommand(null, command);
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		followees = user.readFollowIndex();
		IpfsKey afterToPoll = followees.getNextFolloweeToPoll();
		IpfsFile afterRoot = followees.getLastFetchedRootForFollowee(beforeToPoll);
		Assert.assertEquals(PUBLIC_KEY2, afterToPoll);
		Assert.assertEquals(beforeRoot, afterRoot);
		
		// Check that we see that we failed to update the cache.
		IFolloweeReading followIndex = user.readFollowIndex();
		Assert.assertEquals(0, followIndex.snapshotAllElementsForFollowee(PUBLIC_KEY3).size());
		user2.shutdown();
		user3.shutdown();
		user.shutdown();
	}

	@Test
	public void testTwoFolloweesWithBrokenRecord() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockSwarm swarm = new MockSwarm();
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user3 = new MockUserNode(KEY_NAME, PUBLIC_KEY3, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// We need to add the followees.
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		user.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY2));
		user3.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY3));
		user.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY3));
		
		// We introduce a delay so that the update doesn't happen in the same millisecond (we will wait 100).
		Thread.sleep(100L);
		
		// We should be able to run this multiple times, without it causing problems.
		IFolloweeReading followees = user.readFollowIndex();
		IpfsKey nextKey = followees.getNextFolloweeToPoll();
		// We expect to do the initial check on the first one we added (since it was populated when first read).
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		user.runCommand(null, command);
		nextKey = user.readFollowIndex().getNextFolloweeToPoll();
		// The key should have rotated, even though nothing changed.
		Assert.assertEquals(PUBLIC_KEY3, nextKey);
		
		// Update 2 data elements and remove one of them from the node before refreshing this user.
		File tempFile = FOLDER.newFile();
		FileOutputStream stream = new FileOutputStream(tempFile);
		stream.write("file".getBytes());
		stream.close();
		user3.runCommand(null, new PublishCommand("entry 1", "", null, new ElementSubCommand[] {
				new ElementSubCommand("text/plain", tempFile, 0, 0, false) ,
		}));
		user3.runCommand(null, new PublishCommand("entry 2", "", null, new ElementSubCommand[] {}));
		StreamIndex index = GlobalData.deserializeIndex(user3.loadDataFromNode(user3.resolveKeyOnNode(PUBLIC_KEY3)));
		StreamRecords records = GlobalData.deserializeRecords(user3.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		IpfsFile recordToDelete = IpfsFile.fromIpfsCid(records.getRecord().get(0));
		user3.deleteFile(recordToDelete);
		
		// This should abort, just advancing the next to poll.
		boolean didFail = false;
		try
		{
			user.runCommand(null, command);
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		nextKey = followees.getNextFolloweeToPoll();
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		
		// Check that we see that we did update the cache with the valid entry.
		followees = user.readFollowIndex();
		Assert.assertEquals(0, followees.snapshotAllElementsForFollowee(PUBLIC_KEY3).size());
		user2.shutdown();
		user3.shutdown();
		user.shutdown();
	}

	@Test
	public void testTwoFolloweesWithBrokenLeaf() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockSwarm swarm = new MockSwarm();
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user3 = new MockUserNode(KEY_NAME, PUBLIC_KEY3, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// We need to add the followees.
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		user.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY2));
		user3.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY3));
		user.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY3));
		
		// We should be able to run this multiple times, without it causing problems.
		IFolloweeReading followees = user.readFollowIndex();
		IpfsKey nextKey = followees.getNextFolloweeToPoll();
		// We expect to do the initial check on the first one we added (since it was populated when first read).
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		user.runCommand(null, command);
		nextKey = user.readFollowIndex().getNextFolloweeToPoll();
		// The key should have rotated, even though nothing changed.
		Assert.assertEquals(PUBLIC_KEY3, nextKey);
		
		// Update 2 data elements and remove one of them from the node before refreshing this user.
		File fakeVideo = FOLDER.newFile();
		Files.write(fakeVideo.toPath(), "video".getBytes());
		File fakeImage = FOLDER.newFile();
		Files.write(fakeImage.toPath(), "image".getBytes());
		user3.runCommand(null, new PublishCommand("entry 1", "", null, new ElementSubCommand[] {
				new ElementSubCommand("video/webm", fakeVideo, 720, 1280, false) ,
		}));
		user3.runCommand(null, new PublishCommand("entry 2", "", null, new ElementSubCommand[] {
				new ElementSubCommand("image/jpeg", fakeImage, 720, 1280, true) ,
		}));
		StreamIndex index = GlobalData.deserializeIndex(user3.loadDataFromNode(user3.resolveKeyOnNode(PUBLIC_KEY3)));
		StreamRecords records = GlobalData.deserializeRecords(user3.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		StreamRecord firstRecord = GlobalData.deserializeRecord(user3.loadDataFromNode(IpfsFile.fromIpfsCid(records.getRecord().get(0))));
		IpfsFile leafToDelete = IpfsFile.fromIpfsCid(firstRecord.getElements().getElement().get(0).getCid());
		user3.deleteFile(leafToDelete);
		IpfsFile recordToKeep = IpfsFile.fromIpfsCid(records.getRecord().get(1));
		
		user.runCommand(null, command);
		nextKey = followees.getNextFolloweeToPoll();
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		
		// Check that we see just the one entry in the cache.
		followees = user.readFollowIndex();
		Map<IpfsFile, FollowingCacheElement> cachedEntries = followees.snapshotAllElementsForFollowee(PUBLIC_KEY3);
		Assert.assertEquals(1, cachedEntries.size());
		Assert.assertEquals(recordToKeep, cachedEntries.values().iterator().next().elementHash());
		user2.shutdown();
		user3.shutdown();
		user.shutdown();
	}

	@Test
	public void testOneMissingFollowee() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockSwarm swarm = new MockSwarm();
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// We need to add a followee.
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		user.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY2));
		
		// Run the command once and make sure that the followee key exists.
		user.runCommand(null, command);
		IFolloweeReading reading = user.readFollowIndex();
		IpfsFile lastRoot = reading.getLastFetchedRootForFollowee(PUBLIC_KEY2);
		Assert.assertNotNull(user2.loadDataFromNode(lastRoot));
		long firstMillis = reading.getLastPollMillisForFollowee(PUBLIC_KEY2);
		
		// Now, break the key reference and run it again to make sure the time is updated but not the root (we sleep for a few millis to make sure the clock advances).
		Thread.sleep(2);
		user2.timeoutKey(PUBLIC_KEY2);
		// We should see an exception since there is no key.
		boolean didSucceed;
		try
		{
			user.runCommand(null, command);
			didSucceed = true;
		}
		catch (KeyException e)
		{
			didSucceed = false;
		}
		Assert.assertFalse(didSucceed);
		reading = user.readFollowIndex();
		IpfsFile lastRoot2 = reading.getLastFetchedRootForFollowee(PUBLIC_KEY2);
		Assert.assertNotNull(user2.loadDataFromNode(lastRoot));
		Assert.assertEquals(lastRoot, lastRoot2);
		long secondMillis = reading.getLastPollMillisForFollowee(PUBLIC_KEY2);
		Assert.assertTrue(secondMillis > firstMillis);
		
		user2.shutdown();
		user.shutdown();
	}

	@Test
	public void testOneFolloweeWithAudio() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockSwarm swarm = new MockSwarm();
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// We need to add the followee.
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		user.runCommand(null, new RefreshFolloweeCommand(PUBLIC_KEY2));
		
		// We should be able to run this multiple times, without it causing problems.
		IFolloweeReading followees = user.readFollowIndex();
		IpfsKey nextKey = followees.getNextFolloweeToPoll();
		// We expect to do the initial check on the first one we added (since it was populated when first read).
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		user.runCommand(null, command);
		
		// We want to create 3 data elements, all with special images, but differing in video, audio, and text only.
		File tempImage = FOLDER.newFile();
		File tempVideo = FOLDER.newFile();
		File tempAudio = FOLDER.newFile();
		Files.write(tempImage.toPath(), "image".getBytes());
		Files.write(tempVideo.toPath(), "video".getBytes());
		Files.write(tempAudio.toPath(), "audio".getBytes());
		user2.runCommand(null, new PublishCommand("video", "", null, new ElementSubCommand[] {
				new ElementSubCommand("video/webm", tempVideo, 480, 640, false) ,
				new ElementSubCommand("image/jpeg", tempImage, 480, 640, true) ,
		}));
		user2.runCommand(null, new PublishCommand("audio", "", null, new ElementSubCommand[] {
				new ElementSubCommand("audio/ogg", tempAudio, 0, 0, false) ,
				new ElementSubCommand("image/jpeg", tempImage, 480, 640, true) ,
		}));
		user2.runCommand(null, new PublishCommand("text only", "", null, new ElementSubCommand[] {
				new ElementSubCommand("image/jpeg", tempImage, 480, 640, true) ,
		}));
		
		// Run the refresh command.
		user.runCommand(null, command);
		nextKey = followees.getNextFolloweeToPoll();
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		
		// Generate the expected leaf hashes.
		IpfsFile imageHash = MockSingleNode.generateHash("image".getBytes());
		IpfsFile videoHash = MockSingleNode.generateHash("video".getBytes());
		IpfsFile audioHash = MockSingleNode.generateHash("audio".getBytes());
		
		// Check that we see just the 3 entries in the index, with the appropriate leaves.
		followees = user.readFollowIndex();
		Map<IpfsFile, FollowingCacheElement> cachedEntries = followees.snapshotAllElementsForFollowee(PUBLIC_KEY2);
		List<IpfsFile> elements = List.copyOf(cachedEntries.keySet());
		Assert.assertEquals(3, elements.size());
		// Note that the cache elements aren't exposed to use in a deterministic order.
		int matchedImage = 0;
		int matchedVideo = 0;
		int matchedAudio = 0;
		for (FollowingCacheElement elt : cachedEntries.values())
		{
			if (imageHash.equals(elt.imageHash()))
			{
				matchedImage += 1;
			}
			IpfsFile leaf = elt.leafHash();
			if (null == leaf)
			{
				// This is the default case.
			}
			else if (leaf.equals(videoHash))
			{
				matchedVideo += 1;
			}
			else if (leaf.equals(audioHash))
			{
				matchedAudio += 1;
			}
			else
			{
				// This can't happen.
				Assert.fail();
			}
		}
		Assert.assertEquals(3, matchedImage);
		Assert.assertEquals(1, matchedVideo);
		Assert.assertEquals(1, matchedAudio);
		
		user2.shutdown();
		user.shutdown();
	}
}
