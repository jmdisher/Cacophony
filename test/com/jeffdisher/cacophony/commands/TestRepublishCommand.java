package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public class TestRepublishCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testRepublishAfterNew() throws Throwable
	{
		RepublishCommand command = new RepublishCommand();
		
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand());
		
		// Verify initial update.
		IpfsFile update1 = user.getLastRootElement();
		Assert.assertNotNull(update1);
		
		// Now, run the refresh command.
		user.runCommand(null, command);
		
		// Verify nothing changed.
		IpfsFile update2 = user.getLastRootElement();
		Assert.assertEquals(update1, update2);
		user.shutdown();
	}

	@Test
	public void testMissingChannel() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		RepublishCommand command = new RepublishCommand();
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
