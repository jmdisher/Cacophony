package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecommendations;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestHomeChannelModifier
{
	@Test
	public void testEmpty() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		_populateWithEmpty(access);
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(4, access.writes);
	}

	@Test
	public void testReadWriteEmpty() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		_populateWithEmpty(access);
		access.writes = 0;
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		AbstractDescription desc = modifier.loadDescription();
		AbstractRecords records = modifier.loadRecords();
		AbstractRecommendations recom = modifier.loadRecommendations();
		Assert.assertNotNull(desc);
		Assert.assertNotNull(records);
		Assert.assertNotNull(recom);
		IpfsFile initialRoot = access.root;
		IpfsFile updated = modifier.commitNewRoot();
		Assert.assertEquals(initialRoot, updated);
		Assert.assertEquals(initialRoot, access.root);
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(0, access.writes);
	}

	@Test
	public void testUpdateDescription() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		_populateWithEmpty(access);
		access.writes = 0;
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		AbstractDescription desc = modifier.loadDescription();
		desc.setName("updated name");
		modifier.storeDescription(desc);
		IpfsFile root = modifier.commitNewRoot();
		Assert.assertEquals(access.root, root);
		modifier = new HomeChannelModifier(access);
		desc = modifier.loadDescription();
		Assert.assertEquals("updated name", desc.getName());
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(2, access.writes);
	}

	@Test
	public void testUpdateRecords() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		_populateWithEmpty(access);
		access.writes = 0;
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		AbstractRecords records = modifier.loadRecords();
		records.addRecord(MockSingleNode.generateHash("fake post".getBytes()));
		modifier.storeRecords(records);
		IpfsFile root = modifier.commitNewRoot();
		Assert.assertEquals(access.root, root);
		modifier = new HomeChannelModifier(access);
		records = modifier.loadRecords();
		Assert.assertEquals(1, records.getRecordList().size());
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(2, access.writes);
	}

	@Test
	public void testUpdateRecommendations() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		_populateWithEmpty(access);
		access.writes = 0;
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		AbstractRecommendations recom = modifier.loadRecommendations();
		recom.addUser(IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"));
		modifier.storeRecommendations(recom);
		IpfsFile root = modifier.commitNewRoot();
		Assert.assertEquals(access.root, root);
		modifier = new HomeChannelModifier(access);
		recom = modifier.loadRecommendations();
		Assert.assertEquals(1, recom.getUserList().size());
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(2, access.writes);
	}

	@Test
	public void testEmptyUpdate() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		_populateWithEmpty(access);
		access.writes = 0;
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		// We define it as invalid to commit if nothing has been read so check for the assertion error.
		boolean didFail = false;
		try
		{
			modifier.commitNewRoot();
			Assert.fail();
		}
		catch (AssertionError e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
	}

	@Test
	public void testVacuousUpdate() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		
		// Write the empty data as V1.
		_populateWithEmpty(access);
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(4, access.writes);
		access.writes = 0;
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		modifier.storeDescription(modifier.loadDescription());
		modifier.storeRecords(modifier.loadRecords());
		modifier.storeRecommendations(modifier.loadRecommendations());
		
		// The HomeChannelModifier will save as V2 so read the new elements and make sure that they serialize to V1 with matching hashes.
		IpfsFile initialRootCid = access.root;
		AbstractIndex initialRootElement = access.loadCached(initialRootCid, AbstractIndex.DESERIALIZER).get();
		
		IpfsFile updated = modifier.commitNewRoot();
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(4, access.writes);
		
		AbstractIndex updatedRootElement = access.loadCached(updated, AbstractIndex.DESERIALIZER).get();
		AbstractDescription updatedDescription = access.loadCached(updatedRootElement.descriptionCid, AbstractDescription.DESERIALIZER).get();
		AbstractRecords updatedRecords = access.loadCached(updatedRootElement.recordsCid, AbstractRecords.DESERIALIZER).get();
		AbstractRecommendations updatedRecommendations = access.loadCached(updatedRootElement.recommendationsCid, AbstractRecommendations.DESERIALIZER).get();
		Assert.assertEquals(initialRootElement.descriptionCid, MockSingleNode.generateHash(updatedDescription.serializeV1()));
		Assert.assertEquals(initialRootElement.recordsCid, MockSingleNode.generateHash(updatedRecords.serializeV1()));
		Assert.assertEquals(initialRootElement.recommendationsCid, MockSingleNode.generateHash(updatedRecommendations.serializeV1()));
	}


	private static void _populateWithEmpty(MockWritingAccess access) throws Throwable
	{
		AbstractDescription desc = AbstractDescription.createNew();
		desc.setName("name");
		desc.setDescription("description");
		desc.setUserPic("image/jpeg", MockSingleNode.generateHash("fake picture cid source".getBytes()));
		AbstractRecords records = AbstractRecords.createNew();
		AbstractRecommendations recom = AbstractRecommendations.createNew();
		AbstractIndex index = AbstractIndex.createNew();
		index.descriptionCid = _store(access, desc.serializeV1());
		index.recordsCid = _store(access, records.serializeV1());
		index.recommendationsCid = _store(access, recom.serializeV1());
		access.uploadIndexAndUpdateTracking(index);
	}

	private static IpfsFile _store(MockWritingAccess access, byte[] data) throws Throwable
	{
		return access.uploadAndPin(new ByteArrayInputStream(data));
	}

	private static int _countPins(MockWritingAccess access)
	{
		int count = 0;
		for (Integer i : access.pins.values())
		{
			count += i;
		}
		return count;
	}
}
