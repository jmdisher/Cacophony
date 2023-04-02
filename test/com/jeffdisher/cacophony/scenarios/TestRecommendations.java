package com.jeffdisher.cacophony.scenarios;

import java.io.ByteArrayOutputStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.AddRecommendationCommand;
import com.jeffdisher.cacophony.commands.ListRecommendationsCommand;
import com.jeffdisher.cacophony.commands.RemoveRecommendationCommand;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;


public class TestRecommendations
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME1 = "keyName1";
	private static final IpfsKey PUBLIC_KEY1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo142");

	@Test
	public void testAddRecommendation() throws Throwable
	{
		// We only need a single node.
		MockUserNode user1 = new MockUserNode(KEY_NAME1, PUBLIC_KEY1, new MockSingleNode(new MockSwarm()));
		
		// We only need a single real user.
		user1.createChannel(KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Add the recommendation.
		AddRecommendationCommand addCommand = new AddRecommendationCommand(PUBLIC_KEY2);
		user1.runCommand(null, addCommand);
		
		// List the recommendations - make sure we find the key.
		ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
		ListRecommendationsCommand listCommand = new ListRecommendationsCommand(null);
		user1.runCommand(captureStream, listCommand);
		Assert.assertEquals(">1> Recommendations of z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141:\n"
			+ "=1= 	z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo142\n"
			+ "<1< \n"
			, new String(captureStream.toByteArray())
		);
		
		// Remove the recommendation.
		RemoveRecommendationCommand removeCommand = new RemoveRecommendationCommand(PUBLIC_KEY2);
		user1.runCommand(null, removeCommand);
		
		// List the recommendations - make sure we see an empty list.
		captureStream = new ByteArrayOutputStream();
		listCommand = new ListRecommendationsCommand(null);
		user1.runCommand(captureStream, listCommand);
		Assert.assertEquals(">1> Recommendations of z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141:\n"
				+ "<1< \n"
				, new String(captureStream.toByteArray()));
		user1.shutdown();
	}

	@Test
	public void testUsageErrorNotFollowed() throws Throwable
	{
		// We only need a single node.
		MockUserNode user1 = new MockUserNode(KEY_NAME1, PUBLIC_KEY1, new MockSingleNode(new MockSwarm()));
		
		// We only need a single real user.
		user1.createChannel(KEY_NAME1, "User 1", "Description 1", "User pic 1\n".getBytes());
		
		// Verify that trying to list recommendations for someone we are not following fails.
		ListRecommendationsCommand listCommand = new ListRecommendationsCommand(PUBLIC_KEY2);
		try {
			user1.runCommand(null, listCommand);
			Assert.fail("Exception expected");
		} catch (KeyException e) {
			// Expected.
		}
		user1.shutdown();
	}
}
