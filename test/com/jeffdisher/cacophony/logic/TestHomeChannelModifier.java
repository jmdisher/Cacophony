package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


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
		StreamDescription desc = modifier.loadDescription();
		AbstractRecords records = modifier.loadRecords();
		StreamRecommendations recom = modifier.loadRecommendations();
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
		StreamDescription desc = modifier.loadDescription();
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
		StreamRecommendations recom = modifier.loadRecommendations();
		recom.getUser().add("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
		modifier.storeRecommendations(recom);
		IpfsFile root = modifier.commitNewRoot();
		Assert.assertEquals(access.root, root);
		modifier = new HomeChannelModifier(access);
		recom = modifier.loadRecommendations();
		Assert.assertEquals(1, recom.getUser().size());
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
		_populateWithEmpty(access);
		access.writes = 0;
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		modifier.storeDescription(modifier.loadDescription());
		modifier.storeRecords(modifier.loadRecords());
		modifier.storeRecommendations(modifier.loadRecommendations());
		IpfsFile initialRoot = access.root;
		IpfsFile updated = modifier.commitNewRoot();
		Assert.assertEquals(initialRoot, updated);
		Assert.assertEquals(initialRoot, access.root);
		Assert.assertEquals(4, access.data.size());
		Assert.assertEquals(4, _countPins(access));
		Assert.assertEquals(4, access.writes);
	}


	private static void _populateWithEmpty(MockWritingAccess access) throws Throwable
	{
		StreamDescription desc = new StreamDescription();
		desc.setName("name");
		desc.setDescription("description");
		desc.setPicture(MockSingleNode.generateHash("fake picture cid source".getBytes()).toSafeString());
		StreamRecords records = new StreamRecords();
		StreamRecommendations recom = new StreamRecommendations();
		StreamIndex index = new StreamIndex();
		index.setVersion(1);
		index.setDescription(_storeWithString(access, GlobalData.serializeDescription(desc)));
		index.setRecords(_storeWithString(access, GlobalData.serializeRecords(records)));
		index.setRecommendations(_storeWithString(access, GlobalData.serializeRecommendations(recom)));
		access.uploadIndexAndUpdateTracking(index);
	}

	private static String _storeWithString(MockWritingAccess access, byte[] data) throws Throwable
	{
		return access.uploadAndPin(new ByteArrayInputStream(data)).toSafeString();
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
