package com.jeffdisher.cacophony.scenarios;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.RefreshFolloweeCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.testutils.MockConnection;
import com.jeffdisher.cacophony.testutils.MockLocalActions;
import com.jeffdisher.cacophony.testutils.MockPinMechanism;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestCacheSaturation
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String IPFS_HOST = "ipfsHost";
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
		private final GlobalPinCache _pinCache;
		private final MockPinMechanism _pinMechanism;
		private final FollowIndex _followIndex;
		private final MockConnection _sharedConnection;
		private final MockLocalActions _localActions;
		
		public User(String keyName, IpfsKey publicKey, User upstreamUser)
		{
			MockConnection peer = (null != upstreamUser) ? upstreamUser._sharedConnection : null;
			_keyName = keyName;
			_publicKey = publicKey;
			_pinCache = GlobalPinCache.newCache();
			_pinMechanism = new MockPinMechanism(peer);
			_followIndex = FollowIndex.emptyFollowIndex();
			_sharedConnection = new MockConnection(_keyName, _publicKey, _pinMechanism, peer);
			_localActions = new MockLocalActions(null, null, null, _sharedConnection, _pinCache, _pinMechanism, _followIndex);
		}
		
		public void createChannel(Executor executor, int userNumber) throws IOException
		{
			File userPic = FOLDER.newFile();
			// Create user 1.
			FileOutputStream stream = new FileOutputStream(userPic);
			stream.write(("User pic " + userNumber + "\n").getBytes());
			stream.close();
			
			CreateChannelCommand createChannel = new CreateChannelCommand(IPFS_HOST, _keyName);
			createChannel.scheduleActions(executor, _localActions);
			UpdateDescriptionCommand updateDescription = new UpdateDescriptionCommand("User " + userNumber, "Description " + userNumber, userPic);
			updateDescription.scheduleActions(executor, _localActions);
		}
		
		public void followUser(Executor executor, User followee) throws Throwable
		{
			StartFollowingCommand startFollowingCommand = new StartFollowingCommand(followee._publicKey);
			startFollowingCommand.scheduleActions(executor, _localActions);
		}
		
		public void publish(Executor executor, String name, byte[] thumbnail, byte[] video) throws IOException
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
			publishCommand.scheduleActions(executor, _localActions);
		}
		
		public void pollForUpdates(Executor executor, User followee) throws IOException
		{
			RefreshFolloweeCommand command = new RefreshFolloweeCommand(followee._publicKey);
			command.scheduleActions(executor, _localActions);
		}
		
		public List<String> loadStreamForFollowee(User followee) throws IOException
		{
			StreamIndex index = GlobalData.deserializeIndex(_sharedConnection.loadData(_sharedConnection.resolve(followee._publicKey)));
			return GlobalData.deserializeRecords(_sharedConnection.loadData(IpfsFile.fromIpfsCid(index.getRecords()))).getRecord();
		}
		
		public boolean isDataPresent(byte[] data) throws IOException
		{
			IpfsFile file = MockConnection.generateHash(data);
			byte[] stored = _sharedConnection.loadData(file);
			if (null != stored)
			{
				// Verify that these match, if found, since we shouldn't see collisions.
				Assert.assertArrayEquals(data, stored);
			}
			return (null != stored);
		}
	}
}
