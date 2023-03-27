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
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestEditPostCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final IpfsFile FAKE_ELT = IpfsFile.fromIpfsCid("QmdKwHjDNCDbZWUTu5fAceQMLmDhnpoRcHGNYW3rurJ1ef");

	@Test
	public void testMatch() throws Throwable
	{
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(new MockSwarm()));
		
		// Create the entry.
		String name = "entry name";
		String discussionUrl = null;
		String mime = "text/plain";
		String fileContents = "Testing\n";
		_commonTestOnePublish(user, name, discussionUrl, mime, fileContents);
		
		// Verify the integrity of the data.
		IpfsFile root1 = user.resolveKeyOnNode(PUBLIC_KEY);
		IpfsFile elt1 = _verifyIntegrity(user, name, discussionUrl, mime, fileContents, root1);
		
		// Edit the post.
		String newName = "updated name";
		user.runCommand(null, new EditPostCommand(elt1, newName, null, null));
		
		// Verify the integrity of the data.
		IpfsFile root2 = user.resolveKeyOnNode(PUBLIC_KEY);
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
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(new MockSwarm()));
		
		// Create the entry.
		String name = "entry name";
		String discussionUrl = null;
		String mime = "text/plain";
		String fileContents = "Testing\n";
		_commonTestOnePublish(user, name, discussionUrl, mime, fileContents);
		
		// Verify the integrity of the data.
		IpfsFile root1 = user.resolveKeyOnNode(PUBLIC_KEY);
		IpfsFile elt1 = _verifyIntegrity(user, name, discussionUrl, mime, fileContents, root1);
		
		// Edit the post.
		String newName = "updated name";
		boolean didPass = user.runCommand(null, new EditPostCommand(FAKE_ELT, newName, null, null));
		Assert.assertFalse(didPass);
		
		// Verify that the edit did NOT happen.
		IpfsFile root2 = user.resolveKeyOnNode(PUBLIC_KEY);
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
		user.createEmptyConfig(KEY_NAME);
		boolean didPass = user.runCommand(null, new CreateChannelCommand(KEY_NAME));
		Assert.assertTrue(didPass);
		
		// Now, run the publish command.
		didPass = user.runCommand(null, command);
		Assert.assertTrue(didPass);
	}

	private IpfsFile _verifyIntegrity(MockUserNode user, String name, String discussionUrl, String mime, String fileContents, IpfsFile root) throws FailedDeserializationException, IpfsConnectionException
	{
		StreamIndex index = GlobalData.deserializeIndex(user.loadDataFromNode(root));
		Assert.assertEquals(1, index.getVersion());
		StreamRecords records = GlobalData.deserializeRecords(user.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		List<String> recordCidList = records.getRecord();
		Assert.assertEquals(1, recordCidList.size());
		IpfsFile recordCid = IpfsFile.fromIpfsCid(recordCidList.get(0));
		StreamRecord record = GlobalData.deserializeRecord(user.loadDataFromNode(recordCid));
		Assert.assertEquals(name, record.getName());
		Assert.assertEquals(discussionUrl, record.getDiscussion());
		List<DataElement> dataElements = record.getElements().getElement();
		Assert.assertEquals(1, dataElements.size());
		DataElement elt = dataElements.get(0);
		Assert.assertEquals(mime, elt.getMime());
		Assert.assertEquals(fileContents, new String(user.loadDataFromNode(IpfsFile.fromIpfsCid(elt.getCid()))));
		return recordCid;
	}
}
