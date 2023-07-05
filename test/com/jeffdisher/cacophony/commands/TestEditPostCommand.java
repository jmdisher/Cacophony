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
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;


public class TestEditPostCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";
	private static final IpfsFile FAKE_ELT = MockSingleNode.generateHash(new byte[] {1});

	@Test
	public void testMatch() throws Throwable
	{
		MockUserNode user = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		
		// Create the entry.
		String name = "entry name";
		String discussionUrl = null;
		String mime = "text/plain";
		String fileContents = "Testing\n";
		_commonTestOnePublish(user, name, discussionUrl, mime, fileContents);
		
		// Verify the integrity of the data.
		IpfsFile root1 = user.resolveKeyOnNode(MockKeys.K1);
		IpfsFile elt1 = _verifyIntegrity(user, name, discussionUrl, mime, fileContents, root1);
		
		// Edit the post.
		String newName = "updated name";
		user.runCommand(null, new EditPostCommand(elt1, newName, null, null));
		
		// Verify the integrity of the data.
		IpfsFile root2 = user.resolveKeyOnNode(MockKeys.K1);
		IpfsFile elt2 = _verifyIntegrity(user, newName, discussionUrl, mime, fileContents, root2);
		
		// Verify that the change is correct.
		Assert.assertNotEquals(root1, root2);
		Assert.assertNotEquals(elt1, elt2);
		Assert.assertFalse(user.isPinnedLocally(elt1));
		
		user.shutdown();
	}

	@Test
	public void testMissing() throws Throwable
	{
		MockUserNode user = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		
		// Create the entry.
		String name = "entry name";
		String discussionUrl = null;
		String mime = "text/plain";
		String fileContents = "Testing\n";
		_commonTestOnePublish(user, name, discussionUrl, mime, fileContents);
		
		// Verify the integrity of the data.
		IpfsFile root1 = user.resolveKeyOnNode(MockKeys.K1);
		IpfsFile elt1 = _verifyIntegrity(user, name, discussionUrl, mime, fileContents, root1);
		
		// Edit the post.
		String newName = "updated name";
		boolean didPass = false;
		try
		{
			user.runCommand(null, new EditPostCommand(FAKE_ELT, newName, null, null));
			didPass = true;
		}
		catch (UsageException e)
		{
			didPass = false;
		}
		Assert.assertFalse(didPass);
		
		// Verify that the edit did NOT happen.
		IpfsFile root2 = user.resolveKeyOnNode(MockKeys.K1);
		IpfsFile elt2 = _verifyIntegrity(user, name, discussionUrl, mime, fileContents, root2);
		
		// Verify that the change had no impact on storage
		Assert.assertEquals(root1, root2);
		Assert.assertEquals(elt1, elt2);
		Assert.assertTrue(user.isPinnedLocally(elt1));
		
		user.shutdown();
	}


	private static void _commonTestOnePublish(MockUserNode user, String name, String discussionUrl, String mime, String fileContents) throws Throwable
	{
		File tempFile = FOLDER.newFile();
		FileOutputStream stream = new FileOutputStream(tempFile);
		stream.write(fileContents.getBytes());
		stream.close();
		
		ElementSubCommand[] elements = { new ElementSubCommand(mime, tempFile, 0, 0, false) };
		PublishCommand command = new PublishCommand(name, "description", discussionUrl, elements);
		
		// We need to create the channel first so we will just use the command to do that.
		boolean didPass = (null != user.runCommand(null, new CreateChannelCommand(KEY_NAME)));
		Assert.assertTrue(didPass);
		
		// Now, run the publish command.
		didPass = (null != user.runCommand(null, command));
		Assert.assertTrue(didPass);
	}

	private IpfsFile _verifyIntegrity(MockUserNode user, String name, String discussionUrl, String mime, String fileContents, IpfsFile root) throws FailedDeserializationException, IpfsConnectionException
	{
		AbstractIndex index = AbstractIndex.DESERIALIZER.apply(user.loadDataFromNode(root));
		AbstractRecords records = AbstractRecords.DESERIALIZER.apply(user.loadDataFromNode(index.recordsCid));
		List<IpfsFile> recordCidList = records.getRecordList();
		Assert.assertEquals(1, recordCidList.size());
		IpfsFile recordCid = recordCidList.get(0);
		AbstractRecord record = AbstractRecord.DESERIALIZER.apply(user.loadDataFromNode(recordCid));
		Assert.assertEquals(name, record.getName());
		Assert.assertEquals(discussionUrl, record.getDiscussionUrl());
		List<Leaf> dataElements = record.getVideoExtension();
		Assert.assertEquals(1, dataElements.size());
		Leaf elt = dataElements.get(0);
		Assert.assertEquals(mime, elt.mime());
		Assert.assertEquals(fileContents, new String(user.loadDataFromNode(elt.cid())));
		return recordCid;
	}
}
