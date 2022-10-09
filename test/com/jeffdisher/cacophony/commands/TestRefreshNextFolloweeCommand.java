package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.testutils.MockUserNode;
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
		user.shutdown();
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
		user1.shutdown();
		user.shutdown();
	}

	@Test
	public void testTwoFollowees() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, null);
		MockUserNode user3 = new MockUserNode(KEY_NAME, PUBLIC_KEY3, user2);
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, user3);
		MockUserNode.connectNodes(user, user2);
		
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
		user2.shutdown();
		user3.shutdown();
		user.shutdown();
	}

	@Test
	public void testTwoFolloweesWithBrokenRecordsList() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, null);
		MockUserNode user3 = new MockUserNode(KEY_NAME, PUBLIC_KEY3, user2);
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, user3);
		MockUserNode.connectNodes(user, user2);
		
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
		
		// Note that we expect this to fail, but the command will handle any exceptions internally.
		// The only way we can observe this failure is by scraping the output or observing the root didn't change but the next to poll advanced.
		followees = user.readFollowIndex();
		IpfsKey beforeToPoll = followees.nextKeyToPoll();
		Assert.assertEquals(PUBLIC_KEY3, beforeToPoll);
		IpfsFile beforeRoot = followees.peekRecord(beforeToPoll).lastFetchedRoot();
		user.runCommand(null, command);
		followees = user.readFollowIndex();
		IpfsKey afterToPoll = followees.nextKeyToPoll();
		IpfsFile afterRoot = followees.peekRecord(beforeToPoll).lastFetchedRoot();
		Assert.assertEquals(PUBLIC_KEY2, afterToPoll);
		Assert.assertEquals(beforeRoot, afterRoot);
		
		// Check that we see that we failed to update the cache.
		FollowIndex followIndex = user.readFollowIndex();
		FollowRecord record = followIndex.peekRecord(PUBLIC_KEY3);
		FollowingCacheElement[] elements = record.elements();
		Assert.assertEquals(0, elements.length);
		user2.shutdown();
		user3.shutdown();
		user.shutdown();
	}

	@Test
	public void testTwoFolloweesWithBrokenRecord() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, null);
		MockUserNode user3 = new MockUserNode(KEY_NAME, PUBLIC_KEY3, user2);
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, user3);
		MockUserNode.connectNodes(user, user2);
		
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
		
		// This should abort, just advancing the next to poll.
		user.runCommand(null, command);
		nextKey = followees.nextKeyToPoll();
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		
		// Check that we see that we did update the cache with the valid entry.
		FollowIndex followIndex = user.readFollowIndex();
		FollowRecord record = followIndex.peekRecord(PUBLIC_KEY3);
		FollowingCacheElement[] elements = record.elements();
		Assert.assertEquals(0, elements.length);
		user2.shutdown();
		user3.shutdown();
		user.shutdown();
	}

	@Test
	public void testTwoFolloweesWithBrokenLeaf() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, null);
		MockUserNode user3 = new MockUserNode(KEY_NAME, PUBLIC_KEY3, user2);
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, user3);
		MockUserNode.connectNodes(user, user2);
		
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
		StreamRecord firstRecord = GlobalData.deserializeRecord(user3.loadDataFromNode(IpfsFile.fromIpfsCid(records.getRecord().get(0))));
		IpfsFile leafToDelete = IpfsFile.fromIpfsCid(firstRecord.getElements().getElement().get(0).getCid());
		user3.deleteFile(leafToDelete);
		IpfsFile recordToKeep = IpfsFile.fromIpfsCid(records.getRecord().get(1));
		
		user.runCommand(null, command);
		nextKey = followees.nextKeyToPoll();
		Assert.assertEquals(PUBLIC_KEY2, nextKey);
		
		// Check that we see just the one entry in the cache.
		FollowIndex followIndex = user.readFollowIndex();
		FollowRecord record = followIndex.peekRecord(PUBLIC_KEY3);
		FollowingCacheElement[] elements = record.elements();
		Assert.assertEquals(1, elements.length);
		Assert.assertEquals(recordToKeep, elements[0].elementHash());
		user2.shutdown();
		user3.shutdown();
		user.shutdown();
	}

	@Test
	public void testOneMissingFollowee() throws Throwable
	{
		RefreshNextFolloweeCommand command = new RefreshNextFolloweeCommand();
		
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, null);
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, user2);
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		// We need to add a followee.
		user2.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		user.runCommand(null, new StartFollowingCommand(PUBLIC_KEY2));
		
		// Run the command once and make sure that the followee key exists.
		user.runCommand(null, command);
		FollowRecord record = user.readFollowIndex().peekRecord(PUBLIC_KEY2);
		IpfsFile lastRoot = record.lastFetchedRoot();
		Assert.assertNotNull(user2.loadDataFromNode(lastRoot));
		long firstMillis = record.lastPollMillis();
		
		// Now, break the key reference and run it again to make sure the time is updated but not the root (we sleep for a few millis to make sure the clock advances).
		Thread.sleep(2);
		user2.timeoutKey(PUBLIC_KEY2);
		user.runCommand(null, command);
		record = user.readFollowIndex().peekRecord(PUBLIC_KEY2);
		IpfsFile lastRoot2 = record.lastFetchedRoot();
		Assert.assertNotNull(user2.loadDataFromNode(lastRoot));
		Assert.assertEquals(lastRoot, lastRoot2);
		long secondMillis = record.lastPollMillis();
		Assert.assertTrue(secondMillis > firstMillis);
		
		user2.shutdown();
		user.shutdown();
	}
}
