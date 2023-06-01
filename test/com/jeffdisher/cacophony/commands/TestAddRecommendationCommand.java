package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.UsageException;


public class TestAddRecommendationCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";

	@Test
	public void testMissingChannel() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, null, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		AddRecommendationCommand command = new AddRecommendationCommand(MockKeys.K1);
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
