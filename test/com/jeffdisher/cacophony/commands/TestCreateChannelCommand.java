package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecommendations;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;


public class TestCreateChannelCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";

	@Test
	public void testUsage() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		CreateChannelCommand command = new CreateChannelCommand(KEY_NAME);
		user1.runCommand(null, command);
		
		// Verify the states that should have changed.
		IpfsFile root = user1.resolveKeyOnNode(MockKeys.K1);
		Assert.assertTrue(user1.isInPinCache(root));
		AbstractIndex index = AbstractIndex.DESERIALIZER.apply(user1.loadDataFromNode(root));
		IpfsFile descriptionCid = index.descriptionCid;
		Assert.assertTrue(user1.isInPinCache(descriptionCid));
		AbstractDescription description = AbstractDescription.DESERIALIZER.apply(user1.loadDataFromNode(descriptionCid));
		Assert.assertEquals("Unnamed", description.getName());
		Assert.assertEquals("Description forthcoming", description.getDescription());
		Assert.assertNull(description.getPicCid());
		IpfsFile recommendationsCid = index.recommendationsCid;
		Assert.assertTrue(user1.isInPinCache(recommendationsCid));
		AbstractRecommendations recommendations = AbstractRecommendations.DESERIALIZER.apply(user1.loadDataFromNode(recommendationsCid));
		Assert.assertEquals(0, recommendations.getUserList().size());
		IpfsFile recordsCid = index.recordsCid;
		Assert.assertTrue(user1.isInPinCache(recordsCid));
		AbstractRecords records = AbstractRecords.DESERIALIZER.apply(user1.loadDataFromNode(recordsCid));
		Assert.assertEquals(0, records.getRecordList().size());
		
		Assert.assertTrue(user1.isPinnedLocally(root));
		Assert.assertTrue(user1.isPinnedLocally(descriptionCid));
		Assert.assertTrue(user1.isPinnedLocally(recommendationsCid));
		user1.shutdown();
	}

	@Test
	public void testDuplicateFailure() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		CreateChannelCommand command = new CreateChannelCommand(KEY_NAME);
		user1.runCommand(null, command);
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
