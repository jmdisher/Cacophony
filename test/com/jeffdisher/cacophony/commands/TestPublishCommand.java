package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecord.Leaf;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;


public class TestPublishCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";

	@Test
	public void testUsage() throws Throwable
	{
		String name = "entry name";
		String discussionUrl = null;
		String mime = "text/plain";
		String fileContents = "Testing\n";
		_commonTestOnePublish(name, discussionUrl, mime, fileContents);
	}

	@Test
	public void testWithDiscussionUrl() throws Throwable
	{
		String name = "entry name";
		String discussionUrl = "http://example.com/discussion1";
		String mime = "text/plain";
		String fileContents = "Testing\n";
		_commonTestOnePublish(name, discussionUrl, mime, fileContents);
	}

	@Test
	public void testMissingChannel() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		PublishCommand command = new PublishCommand("name", "description", null, null, null, null, new ElementSubCommand[0]);
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


	private static void _commonTestOnePublish(String name, String discussionUrl, String mime, String fileContents) throws Throwable
	{
		File tempFile = FOLDER.newFile();
		FileOutputStream stream = new FileOutputStream(tempFile);
		stream.write(fileContents.getBytes());
		stream.close();
		
		ElementSubCommand[] elements = { new ElementSubCommand(mime, tempFile, 0, 0) };
		PublishCommand command = new PublishCommand(name, "description", discussionUrl, null, null, null, elements);
		
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user1.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Now, run the publish command.
		user1.runCommand(null, command);
		
		// Verify the states that should have changed.
		IpfsFile root = user1.resolveKeyOnNode(MockKeys.K1);
		AbstractIndex index = AbstractIndex.DESERIALIZER.apply(user1.loadDataFromNode(root));
		AbstractRecords records = AbstractRecords.DESERIALIZER.apply(user1.loadDataFromNode(index.recordsCid));
		List<IpfsFile> recordCidList = records.getRecordList();
		Assert.assertEquals(1, recordCidList.size());
		AbstractRecord record = AbstractRecord.DESERIALIZER.apply(user1.loadDataFromNode(recordCidList.get(0)));
		Assert.assertEquals(name, record.getName());
		Assert.assertEquals(discussionUrl, record.getDiscussionUrl());
		List<Leaf> dataElements = record.getVideoExtension();
		Assert.assertEquals(1, dataElements.size());
		Leaf elt = dataElements.get(0);
		Assert.assertEquals(mime, elt.mime());
		Assert.assertEquals(fileContents, new String(user1.loadDataFromNode(elt.cid())));
		user1.shutdown();
	}
}
