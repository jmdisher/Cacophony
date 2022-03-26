package com.jeffdisher.cacophony.scenarios;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.RefreshFolloweeCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.testutils.MockConnection;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestCacheSaturation
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME0 = "keyName0";
	private static final IpfsKey PUBLIC_KEY0 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14Y");
	private static final String KEY_NAME1 = "keyName1";
	private static final IpfsKey PUBLIC_KEY1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");

	@Test
	public void testIncrementalAddingOneFollowee() throws Throwable
	{
		Executor executor = new Executor(System.out);
		
		User user0 = new User(KEY_NAME0, PUBLIC_KEY0, null);
		User user1 = new User(KEY_NAME1, PUBLIC_KEY1, user0);
		
		user0.createChannel(executor, 0);
		user1.createChannel(executor, 1);
		
		byte[] video0 = "video 0".getBytes();
		byte[] video1 = "video 1".getBytes();
		byte[] video2 = "video 2".getBytes();
		
		// Create one entry before the follow.
		user0.publish(executor, "entry 0", "thumb 0".getBytes(), video0);
		user1.followUser(executor, user0);
		Assert.assertEquals(1, user1.loadStreamForFollowee(user0).size());
		Assert.assertTrue(user1.isDataPresent(video0));
		
		// Now, add another element, poll, and make sure it is present.
		user0.publish(executor, "entry 1", "thumb 1".getBytes(), video1);
		user1.pollForUpdates(executor, user0);
		Assert.assertEquals(2, user1.loadStreamForFollowee(user0).size());
		Assert.assertTrue(user1.isDataPresent(video0));
		Assert.assertTrue(user1.isDataPresent(video1));
		
		// Do this one more time, since now this will be forced to evict something.
		user0.publish(executor, "entry 2", "thumb 2".getBytes(), video2);
		user1.pollForUpdates(executor, user0);
		Assert.assertEquals(3, user1.loadStreamForFollowee(user0).size());
		Assert.assertTrue(user1.isDataPresent(video0));
		Assert.assertTrue(user1.isDataPresent(video1));
		Assert.assertTrue(user1.isDataPresent(video2));
	}

	@Test
	public void testIncrementalSaturationOneFollowee() throws Throwable
	{
		Executor executor = new Executor(System.out);
		
		User user0 = new User(KEY_NAME0, PUBLIC_KEY0, null);
		User user1 = new User(KEY_NAME1, PUBLIC_KEY1, user0);
		
		user0.createChannel(executor, 0);
		user1.createChannel(executor, 1);
		
		byte[] video0 = new byte[40];
		byte[] video1 = new byte[40];
		video1[0] = 1;
		byte[] video2 = new byte[40];
		video2[0] = 2;
		// Create one entry before the follow.
		user0.publish(executor, "entry 0", "thumb 0".getBytes(), video0);
		user1.followUser(executor, user0);
		Assert.assertEquals(1, user1.loadStreamForFollowee(user0).size());
		Assert.assertTrue(user1.isDataPresent(video0));
		
		// Now, add another element, poll, and make sure it is present.
		user0.publish(executor, "entry 1", "thumb 1".getBytes(), video1);
		user1.pollForUpdates(executor, user0);
		Assert.assertEquals(2, user1.loadStreamForFollowee(user0).size());
		Assert.assertTrue(user1.isDataPresent(video0));
		Assert.assertTrue(user1.isDataPresent(video1));
		
		// Do this one more time, since now this will be forced to evict something.
		user0.publish(executor, "entry 2", "thumb 2".getBytes(), video2);
		user1.pollForUpdates(executor, user0);
		Assert.assertEquals(3, user1.loadStreamForFollowee(user0).size());
		// We know that the most recent video will be kept so one of the others must be gone.
		Assert.assertNotEquals(user1.isDataPresent(video0), user1.isDataPresent(video1));
		Assert.assertTrue(user1.isDataPresent(video2));
	}

	@Test
	public void testInitialSaturationOneFollowee() throws Throwable
	{
		Executor executor = new Executor(System.out);
		
		User user0 = new User(KEY_NAME0, PUBLIC_KEY0, null);
		User user1 = new User(KEY_NAME1, PUBLIC_KEY1, user0);
		
		user0.createChannel(executor, 0);
		user1.createChannel(executor, 1);
		
		byte[] video0 = new byte[40];
		byte[] video1 = new byte[40];
		video1[0] = 1;
		byte[] video2 = new byte[40];
		video2[0] = 2;
		// Create one entry before the follow.
		user0.publish(executor, "entry 0", "thumb 0".getBytes(), video0);
		user0.publish(executor, "entry 1", "thumb 1".getBytes(), video1);
		user0.publish(executor, "entry 2", "thumb 2".getBytes(), video2);
		user1.followUser(executor, user0);
		// We know that the most recent video will always be fetched and at most one of the others.
		Assert.assertFalse(user1.isDataPresent(video0) && user1.isDataPresent(video1));
		Assert.assertTrue(user1.isDataPresent(video2));
	}


	private static class User
	{
		private final String _keyName;
		private final IpfsKey _publicKey;
		private final MockUserNode _user;
		
		public User(String keyName, IpfsKey publicKey, User upstreamUser)
		{
			MockUserNode peer = (null != upstreamUser) ? upstreamUser._user : null;
			_keyName = keyName;
			_publicKey = publicKey;
			_user = new MockUserNode(keyName, publicKey, peer);
		}
		
		public void createChannel(Executor executor, int userNumber) throws Throwable
		{
			String name = "User " + userNumber;
			String description = "Description " + userNumber;
			byte[] userPicData = ("User pic " + userNumber + "\n").getBytes();
			
			_user.createChannel(_keyName, name, description, userPicData);
		}
		
		public void followUser(Executor executor, User followee) throws Throwable
		{
			StartFollowingCommand startFollowingCommand = new StartFollowingCommand(followee._publicKey);
			_user.runCommand(executor, startFollowingCommand);
		}
		
		public void publish(Executor executor, String name, byte[] thumbnail, byte[] video) throws Throwable
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
					new ElementSubCommand("video/mp4", videoFile, 720, 1280, false),
					new ElementSubCommand("image/jpeg", thumbnailFile, 0, 0, true),
			};
			PublishCommand publishCommand = new PublishCommand(name, "description", null, elements);
			_user.runCommand(executor, publishCommand);
		}
		
		public void pollForUpdates(Executor executor, User followee) throws Throwable
		{
			RefreshFolloweeCommand command = new RefreshFolloweeCommand(followee._publicKey);
			_user.runCommand(executor, command);
		}
		
		public List<String> loadStreamForFollowee(User followee) throws Throwable
		{
			StreamIndex index = GlobalData.deserializeIndex(_user.loadDataFromNode(_user.resolveKeyOnNode(followee._publicKey)));
			return GlobalData.deserializeRecords(_user.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords()))).getRecord();
		}
		
		public boolean isDataPresent(byte[] data) throws Throwable
		{
			IpfsFile file = MockConnection.generateHash(data);
			byte[] stored = _user.loadDataFromNode(file);
			if (null != stored)
			{
				// Verify that these match, if found, since we shouldn't see collisions.
				Assert.assertArrayEquals(data, stored);
			}
			return (null != stored);
		}
	}
}
