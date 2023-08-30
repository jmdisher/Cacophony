package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;


public class TestSetGlobalPrefsCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";

	@Test
	public void testSetPrefs() throws Throwable
	{
		MockUserNode user = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Verify initial update.
		Assert.assertNotNull(user.getLastRootElement());
		
		PrefsData original = user.readPrefs();
		// Shutdown the environment so that it drops all caches - otherwise, we will end up re-reading the same instance of the prefs object.
		user.shutdown();
		SetGlobalPrefsCommand command = new SetGlobalPrefsCommand(original.videoEdgePixelMax + 1
				, 2000L
				, 4000L
				, 0L
				, original.followeeCacheTargetBytes + 1
				, 3000L
				, 5000L
				, 6000L
				, 7000L
		);
		
		// Now, run the refresh command.
		user.runCommand(null, command);
		
		// Verify the update.
		PrefsData updated = user.readPrefs();
		Assert.assertEquals(original.videoEdgePixelMax + 1, updated.videoEdgePixelMax);
		Assert.assertEquals(original.followeeCacheTargetBytes + 1, updated.followeeCacheTargetBytes);
		Assert.assertEquals(2000L, updated.republishIntervalMillis);
		Assert.assertEquals(3000L, updated.followeeRefreshMillis);
		Assert.assertEquals(4000L, updated.explicitCacheTargetBytes);
		Assert.assertEquals(5000L, updated.followeeRecordThumbnailMaxBytes);
		Assert.assertEquals(6000L, updated.followeeRecordAudioMaxBytes);
		Assert.assertEquals(7000L, updated.followeeRecordVideoMaxBytes);
		user.shutdown();
	}
}
