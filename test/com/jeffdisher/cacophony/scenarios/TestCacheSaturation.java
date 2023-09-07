package com.jeffdisher.cacophony.scenarios;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.RefreshFolloweeCommand;
import com.jeffdisher.cacophony.commands.SetGlobalPrefsCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestCacheSaturation
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME0 = "keyName0";
	private static final String KEY_NAME1 = "keyName1";

	@Test
	public void testIncrementalAddingOneFollowee() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		User user0 = new User(KEY_NAME0, MockKeys.K0, swarm);
		User user1 = new User(KEY_NAME1, MockKeys.K1, swarm);
		
		user0.createChannel(0);
		user1.createChannel(1);
		
		byte[] video0 = "video 0".getBytes();
		byte[] video1 = "video 1".getBytes();
		byte[] video2 = "video 2".getBytes();
		
		// Create one entry before the follow.
		user0.publish("entry 0", "thumb 0".getBytes(), video0);
		user1.followUser(user0);
		Assert.assertEquals(1, user1.loadStreamForFollowee(user0).size());
		Assert.assertTrue(user1.isDataPresent(video0));
		
		// Now, add another element, poll, and make sure it is present.
		user0.publish("entry 1", "thumb 1".getBytes(), video1);
		user1.pollForUpdates(user0);
		Assert.assertEquals(2, user1.loadStreamForFollowee(user0).size());
		Assert.assertTrue(user1.isDataPresent(video0));
		Assert.assertTrue(user1.isDataPresent(video1));
		
		// Do this one more time, since now this will be forced to evict something.
		user0.publish("entry 2", "thumb 2".getBytes(), video2);
		user1.pollForUpdates(user0);
		Assert.assertEquals(3, user1.loadStreamForFollowee(user0).size());
		Assert.assertTrue(user1.isDataPresent(video0));
		Assert.assertTrue(user1.isDataPresent(video1));
		Assert.assertTrue(user1.isDataPresent(video2));
		user0.shutdown();
		user1.shutdown();
	}

	@Test
	public void testIncrementalSaturationOneFollowee() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		User user0 = new User(KEY_NAME0, MockKeys.K0, swarm);
		User user1 = new User(KEY_NAME1, MockKeys.K1, swarm);
		
		user0.createChannel(0);
		user1.createChannel(1);
		
		byte[] video0 = new byte[40];
		byte[] video1 = new byte[40];
		video1[0] = 1;
		byte[] video2 = new byte[40];
		video2[0] = 2;
		// Create one entry before the follow.
		user0.publish("entry 0", "thumb 0".getBytes(), video0);
		user1.followUser(user0);
		Assert.assertEquals(1, user1.loadStreamForFollowee(user0).size());
		Assert.assertTrue(user1.isDataPresent(video0));
		
		// Now, add another element, poll, and make sure it is present.
		user0.publish("entry 1", "thumb 1".getBytes(), video1);
		user1.pollForUpdates(user0);
		Assert.assertEquals(2, user1.loadStreamForFollowee(user0).size());
		Assert.assertTrue(user1.isDataPresent(video0));
		Assert.assertTrue(user1.isDataPresent(video1));
		
		// Do this one more time, since now this will be forced to evict something.
		user0.publish("entry 2", "thumb 2".getBytes(), video2);
		user1.pollForUpdates(user0);
		Assert.assertEquals(3, user1.loadStreamForFollowee(user0).size());
		// We know that the most recent video will be kept so one of the others must be gone.
		Assert.assertNotEquals(user1.isDataPresent(video0), user1.isDataPresent(video1));
		Assert.assertTrue(user1.isDataPresent(video2));
		user0.shutdown();
		user1.shutdown();
	}

	@Test
	public void testInitialSaturationOneFollowee() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		User user0 = new User(KEY_NAME0, MockKeys.K0, swarm);
		User user1 = new User(KEY_NAME1, MockKeys.K1, swarm);
		
		user0.createChannel(0);
		user1.createChannel(1);
		
		byte[] video0 = new byte[40];
		byte[] video1 = new byte[40];
		video1[0] = 1;
		byte[] video2 = new byte[40];
		video2[0] = 2;
		// Create one entry before the follow.
		user0.publish("entry 0", "thumb 0".getBytes(), video0);
		user0.publish("entry 1", "thumb 1".getBytes(), video1);
		user0.publish("entry 2", "thumb 2".getBytes(), video2);
		user1.followUser(user0);
		// We know that the most recent video will always be fetched and at most one of the others.
		Assert.assertFalse(user1.isDataPresent(video0) && user1.isDataPresent(video1));
		Assert.assertTrue(user1.isDataPresent(video2));
		user0.shutdown();
		user1.shutdown();
	}


	private static class User
	{
		private final String _keyName;
		private final IpfsKey _publicKey;
		private final MockUserNode _user;
		
		public User(String keyName, IpfsKey publicKey, MockSwarm swarm) throws IOException
		{
			_keyName = keyName;
			_publicKey = publicKey;
			_user = new MockUserNode(keyName, publicKey, new MockSingleNode(swarm), FOLDER.newFolder());
			// We want these User objects to have a smaller followee cache for more convenient testing.
			try
			{
				_user.runCommand(null, new SetGlobalPrefsCommand(0
						, 0L
						, 0L
						, 0L
						, 100L
						, 0L
						, 0L
						, 0L
						, 0L
				));
			}
			catch (Throwable e)
			{
				// Not expected.
				throw new AssertionError(e);
			}
		}
		
		public void createChannel(int userNumber) throws Throwable
		{
			String name = "User " + userNumber;
			String description = "Description " + userNumber;
			byte[] userPicData = ("User pic " + userNumber + "\n").getBytes();
			
			_user.createChannel(_keyName, name, description, userPicData);
		}
		
		public void followUser(User followee) throws Throwable
		{
			StartFollowingCommand startFollowingCommand = new StartFollowingCommand(followee._publicKey);
			_user.runCommand(null, startFollowingCommand);
		}
		
		public void publish(String name, byte[] thumbnail, byte[] video) throws Throwable
		{
			File thumbnailFile = FOLDER.newFile();
			FileOutputStream dataStream = new FileOutputStream(thumbnailFile);
			dataStream.write(thumbnail);
			dataStream.close();
			File videoFile = FOLDER.newFile();
			dataStream = new FileOutputStream(videoFile);
			dataStream.write(video);
			dataStream.close();
			
			ElementSubCommand[] elements = new ElementSubCommand[] {
					new ElementSubCommand("video/mp4", videoFile, 720, 1280),
			};
			PublishCommand publishCommand = new PublishCommand(name, "description", null, null, "image/jpeg", thumbnailFile, elements);
			_user.runCommand(null, publishCommand);
		}
		
		public void pollForUpdates(User followee) throws Throwable
		{
			RefreshFolloweeCommand command = new RefreshFolloweeCommand(followee._publicKey);
			_user.runCommand(null, command);
		}
		
		public List<String> loadStreamForFollowee(User followee) throws Throwable
		{
			AbstractIndex index = AbstractIndex.DESERIALIZER.apply(_user.loadDataFromNode(_user.resolveKeyOnNode(followee._publicKey)));
			return AbstractRecords.DESERIALIZER.apply(_user.loadDataFromNode(index.recordsCid)).getRecordList().stream().map((IpfsFile cid) -> cid.toSafeString()).toList();
		}
		
		public boolean isDataPresent(byte[] data) throws Throwable
		{
			IpfsFile file = MockSingleNode.generateHash(data);
			byte[] stored = _user.loadDataFromNode(file);
			if (null != stored)
			{
				// Verify that these match, if found, since we shouldn't see collisions.
				Assert.assertArrayEquals(data, stored);
			}
			return (null != stored);
		}
		
		public void shutdown()
		{
			_user.shutdown();
		}
	}
}
