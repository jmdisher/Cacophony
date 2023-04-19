package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public class TestPublishCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

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
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		PublishCommand command = new PublishCommand("name", "description", null, new ElementSubCommand[0]);
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
		
		ElementSubCommand[] elements = { new ElementSubCommand(mime, tempFile, 0, 0, false) };
		PublishCommand command = new PublishCommand(name, "description", discussionUrl, elements);
		
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user1.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Now, run the publish command.
		user1.runCommand(null, command);
		
		// Verify the states that should have changed.
		IpfsFile root = user1.resolveKeyOnNode(PUBLIC_KEY);
		StreamIndex index = GlobalData.deserializeIndex(user1.loadDataFromNode(root));
		Assert.assertEquals(1, index.getVersion());
		StreamRecords records = GlobalData.deserializeRecords(user1.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		List<String> recordCidList = records.getRecord();
		Assert.assertEquals(1, recordCidList.size());
		StreamRecord record = GlobalData.deserializeRecord(user1.loadDataFromNode(IpfsFile.fromIpfsCid(recordCidList.get(0))));
		Assert.assertEquals(name, record.getName());
		Assert.assertEquals(discussionUrl, record.getDiscussion());
		List<DataElement> dataElements = record.getElements().getElement();
		Assert.assertEquals(1, dataElements.size());
		DataElement elt = dataElements.get(0);
		Assert.assertEquals(mime, elt.getMime());
		Assert.assertEquals(fileContents, new String(user1.loadDataFromNode(IpfsFile.fromIpfsCid(elt.getCid()))));
		user1.shutdown();
	}
}
