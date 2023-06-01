package com.jeffdisher.cacophony.scenarios;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.AddRecommendationCommand;
import com.jeffdisher.cacophony.commands.ListRecommendationsCommand;
import com.jeffdisher.cacophony.commands.RemoveRecommendationCommand;
import com.jeffdisher.cacophony.commands.results.KeyList;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.KeyException;


public class TestRecommendations
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME1 = "keyName1";

	@Test
	public void testAddRecommendation() throws Throwable
	{
		// We only need a single node.
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		
		// We only need a single real user.
		user1.createChannel(KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Add the recommendation.
		AddRecommendationCommand addCommand = new AddRecommendationCommand(MockKeys.K2);
		user1.runCommand(null, addCommand);
		
		// List the recommendations - make sure we find the key.
		ListRecommendationsCommand listCommand = new ListRecommendationsCommand(null);
		KeyList result = user1.runCommand(null, listCommand);
		ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
		result.writeHumanReadable(new PrintStream(captureStream));
		Assert.assertEquals("1 keys in list:\n"
				+ "	Recommending: z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14W\n"
				, new String(captureStream.toByteArray())
		);
		
		// Remove the recommendation.
		RemoveRecommendationCommand removeCommand = new RemoveRecommendationCommand(MockKeys.K2);
		user1.runCommand(null, removeCommand);
		
		// List the recommendations - make sure we see an empty list.
		captureStream = new ByteArrayOutputStream();
		listCommand = new ListRecommendationsCommand(null);
		user1.runCommand(null, listCommand);
		result.writeHumanReadable(new PrintStream(captureStream));
		Assert.assertEquals("1 keys in list:\n"
				+ "	Recommending: z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14W\n"
				, new String(captureStream.toByteArray()));
		user1.shutdown();
	}

	@Test
	public void testUsageErrorNotFollowed() throws Throwable
	{
		// We only need a single node.
		MockUserNode user1 = new MockUserNode(KEY_NAME1, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		
		// We only need a single real user.
		user1.createChannel(KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Verify that trying to list recommendations for someone we are not following fails.
		ListRecommendationsCommand listCommand = new ListRecommendationsCommand(MockKeys.K2);
		try {
			user1.runCommand(null, listCommand);
			Assert.fail("Exception expected");
		} catch (KeyException e) {
			// Expected.
		}
		user1.shutdown();
	}
}
