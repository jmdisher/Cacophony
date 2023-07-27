package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.nio.file.Files;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.HomeUserReplyCache;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestRefreshFolloweeCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME = "keyName";

	@Test
	public void testEditVideoPostWithRecordCache() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		LocalRecordCache recordCache = new LocalRecordCache();
		HomeUserReplyCache replyToCache = new HomeUserReplyCache(new HandoffConnector<IpfsFile, IpfsFile>((Runnable run) -> run.run()));
		user1.setContextCaches(recordCache, null, null, replyToCache);
		// Create the users.
		user1.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Add the followee and do the initial refresh.
		user1.runCommand(null, new StartFollowingCommand(MockKeys.K2));
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		// Create a post with a thumbnail and refresh them.
		File tempImage = FOLDER.newFile();
		File tempVideo = FOLDER.newFile();
		Files.write(tempImage.toPath(), "image".getBytes());
		Files.write(tempVideo.toPath(), "video".getBytes());
		OnePost post = user2.runCommand(null, new PublishCommand("Post", null, null, null, "image/jpeg", tempImage, new ElementSubCommand[] {
				new ElementSubCommand("video/webm", tempVideo, 480, 640) ,
		}));
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		// Edit the post and refresh, again.
		user2.runCommand(null, new EditPostCommand(post.recordCid, "Updated name", "Updated description", null));
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		user1.shutdown();
		user2.shutdown();
	}

	@Test
	public void testEditAudioPostWithRecordCache() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		LocalRecordCache recordCache = new LocalRecordCache();
		HomeUserReplyCache replyToCache = new HomeUserReplyCache(new HandoffConnector<IpfsFile, IpfsFile>((Runnable run) -> run.run()));
		user1.setContextCaches(recordCache, null, null, replyToCache);
		// Create the users.
		user1.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Add the followee and do the initial refresh.
		user1.runCommand(null, new StartFollowingCommand(MockKeys.K2));
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		// Create a post with a thumbnail and refresh them.
		File tempImage = FOLDER.newFile();
		File tempAudio = FOLDER.newFile();
		Files.write(tempImage.toPath(), "image".getBytes());
		Files.write(tempAudio.toPath(), "audio".getBytes());
		OnePost post = user2.runCommand(null, new PublishCommand("Post", null, null, null, "image/jpeg", tempImage, new ElementSubCommand[] {
				new ElementSubCommand("audio/ogg", tempAudio, 0, 0) ,
		}));
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		// Edit the post and refresh, again.
		user2.runCommand(null, new EditPostCommand(post.recordCid, "Updated name", "Updated description", null));
		user1.runCommand(null, new RefreshFolloweeCommand(MockKeys.K2));
		
		user1.shutdown();
		user2.shutdown();
	}
}
