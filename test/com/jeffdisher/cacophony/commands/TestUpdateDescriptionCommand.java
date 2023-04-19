package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public class TestUpdateDescriptionCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testMissingChannel() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		UpdateDescriptionCommand command = new UpdateDescriptionCommand("name", "description", null, null, null);
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
