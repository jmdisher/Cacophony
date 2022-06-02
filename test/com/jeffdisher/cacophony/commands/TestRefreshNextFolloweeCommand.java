package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public class TestRefreshNextFolloweeCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");
	private static final IpfsKey PUBLIC_KEY3 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo145");

	@Test
	public void testNoFolloweesException() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, null);
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		boolean didThrow = false;
		try
		{
			user.runCommand(null, command);
		}
		catch (UsageException e)
		{
			didThrow = true;
		}
		Assert.assertTrue(didThrow);
	}

	@Test
	public void testOneFollowee() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, null);
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, user1);
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		// We need to add a followee.
		user1.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		
		// We should be able to run this multiple times, without it causing problems.
		user.runCommand(null, command);
		user.runCommand(null, command);
	}

	@Test
	public void testTwoFollowees() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, null);
		MockUserNode user3 = new MockUserNode(KEY_NAME, PUBLIC_KEY3, user2);
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, user3);
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		// We need to add the followees.
		user2.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		user3.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY3));
		
		// We should be able to run this multiple times, without it causing problems.
		FollowIndex followees = user.readFollowIndex();
		IpfsKey nextKey = followees.nextKeyToPoll();
		// We expect to do the initial check on the first one we added (since it was populated when first read).
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		user.runCommand(null, command);
		nextKey = user.readFollowIndex().nextKeyToPoll();
		// The key should have rotated, even though nothing changed.
		Assert.assertEquals(PUBLIC_KEY3, nextKey);
		user.runCommand(null, command);
		nextKey = followees.nextKeyToPoll();
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
	}

	@Test
	public void testTwoFolloweesWithBrokenRecordsList() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, null);
		MockUserNode user3 = new MockUserNode(KEY_NAME, PUBLIC_KEY3, user2);
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, user3);
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		// We need to add the followees.
		user2.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		user3.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY3));
		
		// We should be able to run this multiple times, without it causing problems.
		FollowIndex followees = user.readFollowIndex();
		IpfsKey nextKey = followees.nextKeyToPoll();
		// We expect to do the initial check on the first one we added (since it was populated when first read).
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		user.runCommand(null, command);
		nextKey = user.readFollowIndex().nextKeyToPoll();
		// The key should have rotated, even though nothing changed.
		Assert.assertEquals(PUBLIC_KEY3, nextKey);
		
		// Update 2 data elements and remove one of them from the node before refreshing this user.
		File tempFile = FOLDER.newFile();
		FileOutputStream stream = new FileOutputStream(tempFile);
		stream.write("file".getBytes());
		stream.close();
		user3.runCommand(null, new PublishCommand("entry 1", "", null, new ElementSubCommand[] {
				new ElementSubCommand("text/plain", tempFile, 0, 0, false) ,
		}));
		user3.runCommand(null, new PublishCommand("entry 2", "", null, new ElementSubCommand[] {}));
		StreamIndex index = GlobalData.deserializeIndex(user3.loadDataFromNode(user3.resolveKeyOnNode(PUBLIC_KEY3)));
		IpfsFile metaDataToDelete = IpfsFile.fromIpfsCid(index.getRecords());
		user3.deleteFile(metaDataToDelete);
		
		boolean didFail = false;
		try
		{
			user.runCommand(null, command);
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		nextKey = followees.nextKeyToPoll();
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		
		// Check that we see that we failed to update the cache.
		FollowIndex followIndex = user.readFollowIndex();
		FollowRecord record = followIndex.getFollowerRecord(PUBLIC_KEY3);
		FollowingCacheElement[] elements = record.elements();
		Assert.assertEquals(0, elements.length);
	}

	@Test
	public void testTwoFolloweesWithBrokenRecord() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, null);
		MockUserNode user3 = new MockUserNode(KEY_NAME, PUBLIC_KEY3, user2);
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, user3);
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		// We need to add the followees.
		user2.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		user3.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY3));
		
		// We should be able to run this multiple times, without it causing problems.
		FollowIndex followees = user.readFollowIndex();
		IpfsKey nextKey = followees.nextKeyToPoll();
		// We expect to do the initial check on the first one we added (since it was populated when first read).
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		user.runCommand(null, command);
		nextKey = user.readFollowIndex().nextKeyToPoll();
		// The key should have rotated, even though nothing changed.
		Assert.assertEquals(PUBLIC_KEY3, nextKey);
		
		// Update 2 data elements and remove one of them from the node before refreshing this user.
		File tempFile = FOLDER.newFile();
		FileOutputStream stream = new FileOutputStream(tempFile);
		stream.write("file".getBytes());
		stream.close();
		user3.runCommand(null, new PublishCommand("entry 1", "", null, new ElementSubCommand[] {
				new ElementSubCommand("text/plain", tempFile, 0, 0, false) ,
		}));
		user3.runCommand(null, new PublishCommand("entry 2", "", null, new ElementSubCommand[] {}));
		StreamIndex index = GlobalData.deserializeIndex(user3.loadDataFromNode(user3.resolveKeyOnNode(PUBLIC_KEY3)));
		StreamRecords records = GlobalData.deserializeRecords(user3.loadDataFromNode(IpfsFile.fromIpfsCid(index.getRecords())));
		IpfsFile recordToDelete = IpfsFile.fromIpfsCid(records.getRecord().get(0));
		user3.deleteFile(recordToDelete);
		
		boolean didFail = false;
		try
		{
			user.runCommand(null, command);
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		nextKey = followees.nextKeyToPoll();
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		
		// Check that we see that we failed to update the cache.
		FollowIndex followIndex = user.readFollowIndex();
		FollowRecord record = followIndex.getFollowerRecord(PUBLIC_KEY3);
		FollowingCacheElement[] elements = record.elements();
		Assert.assertEquals(0, elements.length);
	}
}
