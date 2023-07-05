package com.jeffdisher.cacophony.scenarios;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.AddRecommendationCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.RebroadcastCommand;
import com.jeffdisher.cacophony.commands.RefreshFolloweeCommand;
import com.jeffdisher.cacophony.commands.RemoveEntryFromThisChannelCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.StopFollowingCommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * Tests related to running sequences of operations which specifically demonstrate how a local node manages its pinning
 * behaviour.  This is meant to both test the consistency of those operations, but also demonstrate they nuances of
 * their behaviour as a form of quasi-documentation.
 */
public class TestPinConsistency
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME1 = "keyName1";
	private static final String KEY_NAME2 = "keyName2";

	/**
	 * Demonstrates what happens when a followee is successfully added, then fails to be found, then updates, and can be
	 * found.
	 */
	@Test
	public void followeeNotFound() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME2, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		_commonSetup(user1, user2);
		
		// Break the key and try to refresh.
		user2.timeoutKey(MockKeys.K2);
		boolean didSucceed;
		try
		{
			user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
			didSucceed = true;
		}
		catch (KeyException e)
		{
			didSucceed = false;
		}
		Assert.assertFalse(didSucceed);
		
		// Update user 2 with a new post and refresh.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user2.runCommand(null, publishCommand);
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		// Verify integrity of user 1.
		user1.assertConsistentPinCache();
		
		user1.shutdown();
		user2.shutdown();
	}

	/**
	 * Demonstrates what happens when a followee is makes post which can't be found, then can be found.
	 */
	@Test
	public void followeeElementMissing() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME2, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		_commonSetup(user1, user2);
		
		// Update user 2 with a new post, break the element, and refresh.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user2.runCommand(null, publishCommand);
		byte[] recordData = _readAndBreakElement(user2, MockKeys.K2);
		boolean didFail = false;
		try
		{
			user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		
		// Re-add the record data and refresh again.
		user2.storeDataToNode(recordData);
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		// Verify integrity of user 1.
		user1.assertConsistentPinCache();
		
		user1.shutdown();
		user2.shutdown();
	}

	/**
	 * Demonstrates what happens when a followee makes post which is too big for the protocol, then is delete in favour
	 * of a fixed one.
	 */
	@Test
	public void followeeElementTooBig() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME2, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		_commonSetup(user1, user2);
		
		// Manually update the stream with something too big, then refresh.
		_uploadAsRecord(user2, MockKeys.K2, new byte[(int)SizeLimits.MAX_RECORD_SIZE_BYTES + 1]);
		boolean didFail = false;
		try
		{
			user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		}
		catch (SizeConstraintException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		// Delete the post and create a normal one.
		_removeAllRecords(user2, MockKeys.K2);
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user2.runCommand(null, publishCommand);
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		// Verify integrity of user 1.
		user1.assertConsistentPinCache();
		
		user1.shutdown();
		user2.shutdown();
	}

	/**
	 * Demonstrates what happens when a followee makes a post with a leaf which is unreachable, then makes another post
	 * which contains it.
	 */
	@Test
	public void followeeLeafUnreachable() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME2, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		_commonSetup(user1, user2);
		
		// Make the first post but break the image leaf element, and refresh.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user2.runCommand(null, publishCommand);
		_breakImageLeaf(user2, MockKeys.K2);
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		// Make another post which references the same file, and refresh.
		publishCommand = _createPublishCommand("entry 2", imageFileString, videoFileString);
		user2.runCommand(null, publishCommand);
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		// Verify integrity of user 1.
		user1.assertConsistentPinCache();
		
		user1.shutdown();
		user2.shutdown();
	}

	/**
	 * Demonstrates what happens when a followee is successfully added and then removed, with no overlapping elements.
	 */
	@Test
	public void addRemoveFolloweeNoOverlap() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME2, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		_commonSetup(user1, user2);
		
		// Update user 2 with a new post and refresh.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user2.runCommand(null, publishCommand);
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		// Stop following them.
		user1.runCommand(null, new StopFollowingCommand(MockKeys.K2));
		
		// Verify integrity of user 1.
		user1.assertConsistentPinCache();
		
		user1.shutdown();
		user2.shutdown();
	}

	/**
	 * Demonstrates what happens when a followee is successfully added and then removed, with overlapping elements and
	 * user description.
	 */
	@Test
	public void addRemoveFolloweeLocalOverlap() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME2, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// Create user 1.
		user1.createChannel(KEY_NAME1, "User", "Description", "User pic\n".getBytes());
		
		// Create user 2.
		user2.createChannel(KEY_NAME2, "User", "Description", "User pic\n".getBytes());
		
		// Verify that user 1 can follow user 2.
		user1.runCommand(null, new StartFollowingCommand(MockKeys.K2));
		
		// Make the same post as user 1 and 2, the refresh.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user1.runCommand(null, publishCommand);
		user2.runCommand(null, publishCommand);
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		// Stop following them.
		user1.runCommand(null, new StopFollowingCommand(MockKeys.K2));
		
		// Verify integrity of user 1.
		user1.assertConsistentPinCache();
		
		user1.shutdown();
		user2.shutdown();
	}

	/**
	 * Demonstrates what happens when a user adds and then removes a post.
	 */
	@Test
	public void addRemovePost() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// Create user 1.
		user1.createChannel(KEY_NAME1, "User", "Description", "User pic\n".getBytes());
		
		// Make the post.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user1.runCommand(null, publishCommand);
		
		// Delete the post.
		IpfsFile post = _getMostRecendRecord(user1, MockKeys.K1);
		user1.runCommand(null, new RemoveEntryFromThisChannelCommand(post));
		
		// Verify integrity of user 1.
		user1.assertConsistentPinCache();
		
		user1.shutdown();
	}

	/**
	 * Demonstrates what happens when a user adds and then removes a post where it overlaps leaves with another post.
	 */
	@Test
	public void addRemovePostWithOverlap() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// Create user 1.
		user1.createChannel(KEY_NAME1, "User", "Description", "User pic\n".getBytes());
		
		// Make the posts.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand1 = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user1.runCommand(null, publishCommand1);
		PublishCommand publishCommand2 = _createPublishCommand("entry 2", imageFileString, videoFileString);
		user1.runCommand(null, publishCommand2);
		
		// Delete the second post.
		IpfsFile post = _getMostRecendRecord(user1, MockKeys.K1);
		user1.runCommand(null, new RemoveEntryFromThisChannelCommand(post));
		
		// Verify integrity of user 1.
		user1.assertConsistentPinCache();
		
		user1.shutdown();
	}

	/**
	 * Demonstrates what happens when a user changes their user pic.
	 */
	@Test
	public void changeUserPic() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// Create user 1.
		user1.createChannel(KEY_NAME1, "User", "Description", "User pic\n".getBytes());
		
		// Change the picture.
		UpdateDescriptionCommand command = new UpdateDescriptionCommand(null, null, new ByteArrayInputStream("new pic\n".getBytes()), null, null);
		user1.runCommand(null, command);
		
		// Verify integrity of user 1.
		user1.assertConsistentPinCache();
		
		user1.shutdown();
	}

	/**
	 * Demonstrates what happens when a recommendations are updated while following someone with an empty list.
	 */
	@Test
	public void addRecommendation() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME2, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		_commonSetup(user1, user2);
		
		// Add the recommendation.
		AddRecommendationCommand command = new AddRecommendationCommand(MockKeys.K2);
		user1.runCommand(null, command);
		
		// Stop following them.
		user1.runCommand(null, new StopFollowingCommand(MockKeys.K2));
		
		// Verify integrity of user 1.
		user1.assertConsistentPinCache();
		
		user1.shutdown();
		user2.shutdown();
	}

	/**
	 * Demonstrates what happens a followee post is rebroadcasted and then both users delete the post.
	 */
	@Test
	public void rebroadcastAndDelete() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME2, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		_commonSetup(user1, user2);
		
		// Update user 2 with a new post and refresh.
		String videoFileString = "VIDEO FILE\n";
		String imageFileString = "IMAGE\n";
		PublishCommand publishCommand = _createPublishCommand("entry 1", imageFileString, videoFileString);
		user2.runCommand(null, publishCommand);
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		// Rebroadcast.
		IpfsFile post = _getMostRecendRecord(user2, MockKeys.K2);
		RebroadcastCommand command = new RebroadcastCommand(post);
		user1.runCommand(null, command);
		
		// Delete from both and refresh.
		user1.runCommand(null, new RemoveEntryFromThisChannelCommand(post));
		user2.runCommand(null, new RemoveEntryFromThisChannelCommand(post));
		
		// Stop following them.
		user1.runCommand(null, new StopFollowingCommand(MockKeys.K2));
		
		// Verify integrity of user 1.
		user1.assertConsistentPinCache();
		
		user1.shutdown();
		user2.shutdown();
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

	private void _commonSetup(MockUserNode user1, MockUserNode user2) throws Throwable
	{
		// Create user 1.
		user1.createChannel(KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Create user 2.
		user2.createChannel(KEY_NAME2, "User 2", "Description 2", "User pic 2\n".getBytes());
		
		// Verify that user 1 can follow user 2.
		user1.runCommand(null, new StartFollowingCommand(MockKeys.K2));
	}

	private byte[] _readAndBreakElement(MockUserNode userNode, IpfsKey publicKey) throws FailedDeserializationException, IpfsConnectionException
	{
		AbstractIndex index = AbstractIndex.DESERIALIZER.apply(userNode.loadDataFromNode(userNode.resolveKeyOnNode(publicKey)));
		AbstractRecords records = AbstractRecords.DESERIALIZER.apply(userNode.loadDataFromNode(index.recordsCid));
		Assert.assertEquals(1, records.getRecordList().size());
		IpfsFile elt = records.getRecordList().get(0);
		byte[] data = userNode.loadDataFromNode(elt);
		userNode.deleteFile(elt);
		return data;
	}

	private void _uploadAsRecord(MockUserNode userNode, IpfsKey publicKey, byte[] data) throws FailedDeserializationException, IpfsConnectionException, SizeConstraintException
	{
		IpfsFile originalRootCid = userNode.resolveKeyOnNode(publicKey);
		AbstractIndex index = AbstractIndex.DESERIALIZER.apply(userNode.loadDataFromNode(originalRootCid));
		userNode.deleteFile(originalRootCid);
		AbstractRecords records = AbstractRecords.DESERIALIZER.apply(userNode.loadDataFromNode(index.recordsCid));
		userNode.deleteFile(index.recordsCid);
		Assert.assertEquals(0, records.getRecordList().size());
		IpfsFile elt = userNode.storeDataToNode(data);
		records.addRecord(elt);
		IpfsFile newRecords = userNode.storeDataToNode(records.serializeV1());
		index.recordsCid = newRecords;
		IpfsFile newIndex = userNode.storeDataToNode(index.serializeV1());
		userNode.manualPublishLocal(newIndex);
	}

	private void _removeAllRecords(MockUserNode userNode, IpfsKey publicKey) throws FailedDeserializationException, IpfsConnectionException, SizeConstraintException
	{
		IpfsFile originalRootCid = userNode.resolveKeyOnNode(publicKey);
		AbstractIndex index = AbstractIndex.DESERIALIZER.apply(userNode.loadDataFromNode(originalRootCid));
		userNode.deleteFile(originalRootCid);
		AbstractRecords records = AbstractRecords.DESERIALIZER.apply(userNode.loadDataFromNode(index.recordsCid));
		userNode.deleteFile(index.recordsCid);
		Assert.assertEquals(1, records.getRecordList().size());
		userNode.deleteFile(records.getRecordList().get(0));
		records.removeRecord(records.getRecordList().get(0));
		IpfsFile newRecords = userNode.storeDataToNode(records.serializeV1());
		index.recordsCid = newRecords;
		IpfsFile newIndex = userNode.storeDataToNode(index.serializeV1());
		userNode.manualPublishLocal(newIndex);
	}

	private IpfsFile _getMostRecendRecord(MockUserNode userNode, IpfsKey publicKey) throws FailedDeserializationException, IpfsConnectionException, SizeConstraintException
	{
		IpfsFile originalRootCid = userNode.resolveKeyOnNode(publicKey);
		AbstractIndex index = AbstractIndex.DESERIALIZER.apply(userNode.loadDataFromNode(originalRootCid));
		AbstractRecords records = AbstractRecords.DESERIALIZER.apply(userNode.loadDataFromNode(index.recordsCid));
		Assert.assertTrue(records.getRecordList().size() > 0);
		return records.getRecordList().get(records.getRecordList().size() - 1);
	}

	private void _breakImageLeaf(MockUserNode userNode, IpfsKey publicKey) throws FailedDeserializationException, IpfsConnectionException, SizeConstraintException
	{
		IpfsFile originalRootCid = userNode.resolveKeyOnNode(publicKey);
		AbstractIndex index = AbstractIndex.DESERIALIZER.apply(userNode.loadDataFromNode(originalRootCid));
		AbstractRecords records = AbstractRecords.DESERIALIZER.apply(userNode.loadDataFromNode(index.recordsCid));
		Assert.assertEquals(1, records.getRecordList().size());
		AbstractRecord record = AbstractRecord.DESERIALIZER.apply(userNode.loadDataFromNode(records.getRecordList().get(0)));
		userNode.deleteFile(record.getThumbnailCid());
	}
}
