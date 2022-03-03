package com.jeffdisher.cacophony.commands;

import java.io.File;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public class TestHtmlOutputCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testWithoutChannel() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY, null);
		
		File parentDirectory = FOLDER.newFolder();
		HtmlOutputCommand command = new HtmlOutputCommand(new File(parentDirectory, "output"));
		try {
			user1.runCommand(null, command);
			Assert.fail();
		} catch (UsageException e) {
			// Expected.
		}
	}

	@Test
	public void testWithBasicChannel() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY, null);
		CreateChannelCommand createCommand = new CreateChannelCommand(IPFS_HOST, KEY_NAME);
		user1.runCommand(null, createCommand);
		
		File parentDirectory = FOLDER.newFolder();
		HtmlOutputCommand command = new HtmlOutputCommand(new File(parentDirectory, "output"));
		user1.runCommand(null, command);
		
		// TODO:  Verify the presence of on-disk structures once implemented.
	}
}
