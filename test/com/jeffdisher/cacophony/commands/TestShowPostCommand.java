package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.SizeLimits;


public class TestShowPostCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";

	@Test
	public void validPost() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode writeNode = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		String title = "valid post";
		byte[] fakeImage = "image".getBytes();
		MockUserNode readNode = new MockUserNode(null, null, new MockSingleNode(swarm), FOLDER.newFolder());
		IpfsFile postCid = _storeRecord(writeNode, title, fakeImage, MockKeys.K1);
		
		// Make sure that we see no error when this is valid.
		ShowPostCommand.PostDetails details = readNode.runCommand(null, new ShowPostCommand(postCid, false));
		Assert.assertEquals(title, details.name());
		Assert.assertEquals(MockSingleNode.generateHash(fakeImage), details.thumbnailCid());
		Assert.assertNull(details.audioCid());
		Assert.assertNull(details.videoCid());
		
		readNode.shutdown();
		// Write node never started.
	}

	@Test
	public void oversizedPost() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		IpfsFile postCid = user1.storeDataToNode(new byte[(int)SizeLimits.MAX_RECORD_SIZE_BYTES + 1]);
		
		// We should see an error.
		boolean didFail;
		try
		{
			user1.runCommand(null, new ShowPostCommand(postCid, false));
			didFail = false;
		}
		catch (SizeConstraintException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		user1.shutdown();
	}

	@Test
	public void corruptPost() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		IpfsFile postCid = user1.storeDataToNode(new byte[] { 1, 2, 3, 4 });
		
		// We should see an error.
		boolean didFail;
		try
		{
			user1.runCommand(null, new ShowPostCommand(postCid, false));
			didFail = false;
		}
		catch (FailedDeserializationException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		user1.shutdown();
	}

	@Test
	public void explicitCached() throws Throwable
	{
		// We want to show that we still load the full data from the explicit cache, even if the recordCache has a non-cached version.
		// Set everything up as normal.
		MockSwarm swarm = new MockSwarm();
		MockUserNode writeNode = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		String title = "valid post";
		byte[] fakeImage = "image".getBytes();
		MockUserNode readNode = new MockUserNode(null, null, new MockSingleNode(swarm), FOLDER.newFolder());
		IpfsFile postCid = _storeRecord(writeNode, title, fakeImage, MockKeys.K1);
		
		// We want to create a special context which allows us to slide in a LocalRecordCache.
		LocalRecordCache specialRecordCache = new LocalRecordCache();
		specialRecordCache.recordMetaDataPinned(postCid, title, "", 1L, null, MockKeys.K1, 1);
		Context specialContext = readNode.getContext().cloneWithExtras(specialRecordCache, null, null);
		// Verify that we only see the non-cached version when reading this since it hasn't been populated in the explicit cache, yet.
		ShowPostCommand.PostDetails details = new ShowPostCommand(postCid, false).runInContext(specialContext);
		Assert.assertEquals(title, details.name());
		Assert.assertFalse(details.isKnownToBeCached());
		Assert.assertNull(details.thumbnailCid());
		Assert.assertNull(details.audioCid());
		Assert.assertNull(details.videoCid());
		
		// Now, run with the normal context which doesn't have this cache, showing that we will populate it into the explicit cache.
		details = readNode.runCommand(null, new ShowPostCommand(postCid, false));
		Assert.assertEquals(title, details.name());
		Assert.assertTrue(details.isKnownToBeCached());
		Assert.assertEquals(MockSingleNode.generateHash(fakeImage), details.thumbnailCid());
		Assert.assertNull(details.audioCid());
		Assert.assertNull(details.videoCid());
		
		// Now, use the special context again but observe that we still read through to the explicit cache and see the full result.
		// (this approach doesn't force the cache but will use the cached value if it is there and the value it has isn't fully cached).
		details = new ShowPostCommand(postCid, false).runInContext(specialContext);
		Assert.assertEquals(title, details.name());
		Assert.assertTrue(details.isKnownToBeCached());
		Assert.assertEquals(MockSingleNode.generateHash(fakeImage), details.thumbnailCid());
		Assert.assertNull(details.audioCid());
		Assert.assertNull(details.videoCid());
		
		readNode.shutdown();
		// Write node never started.
	}

	@Test
	public void forceCache() throws Throwable
	{
		// We want to show that forceCache will force a full cache for partially-cached elements.
		// Set everything up as normal.
		MockSwarm swarm = new MockSwarm();
		MockUserNode writeNode = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		String title = "valid post";
		byte[] fakeImage = "image".getBytes();
		MockUserNode readNode = new MockUserNode(null, null, new MockSingleNode(swarm), FOLDER.newFolder());
		IpfsFile postCid = _storeRecord(writeNode, title, fakeImage, MockKeys.K1);
		
		// We want to create a special context which allows us to slide in a LocalRecordCache.
		LocalRecordCache specialRecordCache = new LocalRecordCache();
		specialRecordCache.recordMetaDataPinned(postCid, title, "", 1L, null, MockKeys.K1, 1);
		Context specialContext = readNode.getContext().cloneWithExtras(specialRecordCache, null, null);
		// Verify that we only see the non-cached version when reading this since it hasn't been populated in the explicit cache, yet.
		ShowPostCommand.PostDetails details = new ShowPostCommand(postCid, false).runInContext(specialContext);
		Assert.assertEquals(title, details.name());
		Assert.assertFalse(details.isKnownToBeCached());
		Assert.assertNull(details.thumbnailCid());
		Assert.assertNull(details.audioCid());
		Assert.assertNull(details.videoCid());
		
		// Now, show that we DO see the cached variant if requested.
		details = new ShowPostCommand(postCid, true).runInContext(specialContext);
		Assert.assertEquals(title, details.name());
		Assert.assertTrue(details.isKnownToBeCached());
		Assert.assertEquals(MockSingleNode.generateHash(fakeImage), details.thumbnailCid());
		Assert.assertNull(details.audioCid());
		Assert.assertNull(details.videoCid());
		
		// Re-run without that argument to see that we now see the cached result.
		details = new ShowPostCommand(postCid, false).runInContext(specialContext);
		Assert.assertEquals(title, details.name());
		Assert.assertTrue(details.isKnownToBeCached());
		Assert.assertEquals(MockSingleNode.generateHash(fakeImage), details.thumbnailCid());
		Assert.assertNull(details.audioCid());
		Assert.assertNull(details.videoCid());
		
		readNode.shutdown();
		// Write node never started.
	}


	private static IpfsFile _storeRecord(MockUserNode node, String title, byte[] thumbnailData, IpfsKey publisher) throws IpfsConnectionException, SizeConstraintException
	{
		StreamRecord record = new StreamRecord();
		record.setName(title);
		record.setDescription("description");
		record.setPublishedSecondsUtc(10L);
		record.setPublisherKey(publisher.toPublicKey());
		DataArray eltArray = new DataArray();
		record.setElements(eltArray);
		if (null != thumbnailData)
		{
			IpfsFile eltCid = node.storeDataToNode(thumbnailData);
			DataElement element = new DataElement();
			element.setMime("image/jpeg");
			element.setSpecial(ElementSpecialType.IMAGE);
			element.setCid(eltCid.toSafeString());
			eltArray.getElement().add(element);
		}
		byte[] serialized = GlobalData.serializeRecord(record);
		return node.storeDataToNode(serialized);
	}
}
