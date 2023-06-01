package com.jeffdisher.cacophony.scenarios;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.DeleteChannelCommand;
import com.jeffdisher.cacophony.commands.EditPostCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.RemoveEntryFromThisChannelCommand;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.HandoffConnector.IHandoffListener;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * Tests related to multi-channel home data manipulation with populated Context cache objects (effectively simulating a
 * sequence of commands in interactive mode as it just sits on top of the commands).
 */
public class TestMultiChannelContextCaches
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME1 = "keyName1";

	@Test
	public void createDestroyOneUser() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode home = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		LocalRecordCache recordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		EntryCacheRegistry entryRegistry = new EntryCacheRegistry.Builder((Runnable r) -> r.run(), 0).buildRegistry(null);
		home.setContextCaches(recordCache, userInfoCache, entryRegistry);
		
		// Create the channel.
		home.runCommand(null, new CreateChannelCommand(KEY_NAME1));
		
		// Check the status of the caches.
		CombinedListener combined = new CombinedListener();
		Assert.assertEquals(0, recordCache.getKeys().size());
		LocalUserInfoCache.Element elt = userInfoCache.getUserInfo(MockKeys.K1);
		Assert.assertNotNull(elt);
		entryRegistry.getCombinedConnector().registerListener(combined, 0);
		Assert.assertEquals(0, combined.entries.size());
		
		// Delete the channel.
		home.runCommand(null, new DeleteChannelCommand());
		
		// Check the caches.
		Assert.assertEquals(0, recordCache.getKeys().size());
		elt = userInfoCache.getUserInfo(MockKeys.K1);
		Assert.assertNull(elt);
		Assert.assertEquals(0, combined.entries.size());
		
		home.shutdown();
	}

	@Test
	public void postAsOneUser() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode home = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		LocalRecordCache recordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		EntryCacheRegistry entryRegistry = new EntryCacheRegistry.Builder((Runnable r) -> r.run(), 0).buildRegistry(null);
		home.setContextCaches(recordCache, userInfoCache, entryRegistry);
		
		// Create the channel.
		home.runCommand(null, new CreateChannelCommand(KEY_NAME1));
		
		// Make a basic publish.
		File thumbnail = FOLDER.newFile();
		Files.write(thumbnail.toPath(), new byte[] { 1, 2, 3});
		home.runCommand(null, new PublishCommand("name", "description", null, new ElementSubCommand[] {
				new ElementSubCommand("image/jpeg", thumbnail, 0, 0, true)
		}));
		
		// Check the status of the caches.
		CombinedListener combined = new CombinedListener();
		Assert.assertEquals(1, recordCache.getKeys().size());
		LocalUserInfoCache.Element elt = userInfoCache.getUserInfo(MockKeys.K1);
		Assert.assertNotNull(elt);
		entryRegistry.getCombinedConnector().registerListener(combined, 0);
		Assert.assertEquals(1, combined.entries.size());
		
		// Delete the channel.
		home.runCommand(null, new DeleteChannelCommand());
		
		// Check the caches.
		Assert.assertEquals(0, recordCache.getKeys().size());
		elt = userInfoCache.getUserInfo(MockKeys.K1);
		Assert.assertNull(elt);
		Assert.assertEquals(0, combined.entries.size());
		
		home.shutdown();
	}

	@Test
	public void editAsOneUser() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode home = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		LocalRecordCache recordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		EntryCacheRegistry entryRegistry = new EntryCacheRegistry.Builder((Runnable r) -> r.run(), 0).buildRegistry(null);
		home.setContextCaches(recordCache, userInfoCache, entryRegistry);
		
		// Create the channel.
		home.runCommand(null, new CreateChannelCommand(KEY_NAME1));
		
		// Make a basic publish.
		File thumbnail = FOLDER.newFile();
		Files.write(thumbnail.toPath(), new byte[] { 1, 2, 3});
		OnePost newPost = home.runCommand(null, new PublishCommand("name", "description", null, new ElementSubCommand[] {
				new ElementSubCommand("image/jpeg", thumbnail, 0, 0, true)
		}));
		
		// Check the status of the caches.
		CombinedListener combined = new CombinedListener();
		Assert.assertEquals(1, recordCache.getKeys().size());
		LocalUserInfoCache.Element elt = userInfoCache.getUserInfo(MockKeys.K1);
		Assert.assertNotNull(elt);
		entryRegistry.getCombinedConnector().registerListener(combined, 0);
		Assert.assertEquals(1, combined.entries.size());
		
		// Edit the post.
		home.runCommand(null, new EditPostCommand(newPost.recordCid, "Updated name", "New description", "http://example.com"));
		
		// Check the caches.
		Assert.assertEquals(1, recordCache.getKeys().size());
		elt = userInfoCache.getUserInfo(MockKeys.K1);
		Assert.assertNotNull(elt);
		Assert.assertEquals(1, combined.entries.size());
		
		// Delete the channel.
		home.runCommand(null, new DeleteChannelCommand());
		
		// Check the caches.
		Assert.assertEquals(0, recordCache.getKeys().size());
		elt = userInfoCache.getUserInfo(MockKeys.K1);
		Assert.assertNull(elt);
		Assert.assertEquals(0, combined.entries.size());
		
		home.shutdown();
	}

	@Test
	public void removeAsOneUser() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode home = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		LocalRecordCache recordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		EntryCacheRegistry entryRegistry = new EntryCacheRegistry.Builder((Runnable r) -> r.run(), 0).buildRegistry(null);
		home.setContextCaches(recordCache, userInfoCache, entryRegistry);
		
		// Create the channel.
		home.runCommand(null, new CreateChannelCommand(KEY_NAME1));
		
		// Make a basic publish.
		File thumbnail = FOLDER.newFile();
		Files.write(thumbnail.toPath(), new byte[] { 1, 2, 3});
		OnePost newPost = home.runCommand(null, new PublishCommand("name", "description", null, new ElementSubCommand[] {
				new ElementSubCommand("image/jpeg", thumbnail, 0, 0, true)
		}));
		
		// Check the status of the caches.
		CombinedListener combined = new CombinedListener();
		Assert.assertEquals(1, recordCache.getKeys().size());
		LocalUserInfoCache.Element elt = userInfoCache.getUserInfo(MockKeys.K1);
		Assert.assertNotNull(elt);
		entryRegistry.getCombinedConnector().registerListener(combined, 0);
		Assert.assertEquals(1, combined.entries.size());
		
		// Edit the post.
		home.runCommand(null, new RemoveEntryFromThisChannelCommand(newPost.recordCid));
		
		// Check the caches.
		Assert.assertEquals(0, recordCache.getKeys().size());
		elt = userInfoCache.getUserInfo(MockKeys.K1);
		Assert.assertNotNull(elt);
		Assert.assertEquals(0, combined.entries.size());
		
		// Delete the channel.
		home.runCommand(null, new DeleteChannelCommand());
		
		// Check the caches.
		Assert.assertEquals(0, recordCache.getKeys().size());
		elt = userInfoCache.getUserInfo(MockKeys.K1);
		Assert.assertNull(elt);
		Assert.assertEquals(0, combined.entries.size());
		
		home.shutdown();
	}


	private static class CombinedListener implements IHandoffListener<IpfsFile, Void>
	{
		public Set<IpfsFile> entries = new HashSet<>();
		
		@Override
		public boolean create(IpfsFile key, Void value, boolean isNewest)
		{
			Assert.assertFalse(this.entries.contains(key));
			this.entries.add(key);
			return true;
		}
		@Override
		public boolean update(IpfsFile key, Void value)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public boolean destroy(IpfsFile key)
		{
			Assert.assertTrue(this.entries.contains(key));
			this.entries.remove(key);
			return true;
		}
		@Override
		public boolean specialChanged(String special)
		{
			throw new AssertionError("Not in test");
		}
	}
}
