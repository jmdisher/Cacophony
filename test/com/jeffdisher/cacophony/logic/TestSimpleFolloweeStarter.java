package com.jeffdisher.cacophony.logic;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.KeyException;


public class TestSimpleFolloweeStarter
{
	// These values were determined experimentally to fit with the data in this test, not just random values.
	private static final IpfsFile EXPECTED_ROOT = IpfsFile.fromIpfsCid("QmaAXpSHKmT9HeLSMF2anZy1XgcEPPBTQfJ3R9pkds1mJP");
	// Note that the fake will be a re-written root with V2 serialization and a synthesized empty "records" element.
	private static final IpfsFile EXPECTED_FAKE = IpfsFile.fromIpfsCid("QmVio8oyaxQVWP4GC5DVn8CfSoTuX6bsUrQe2fkm2LvxCo");

	@Test
	public void testInitialSetup() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		IpfsFile root = _populateWithOneElement(access);
		Assert.assertEquals(6, access.data.size());
		Assert.assertEquals(0, access.pins.values().size());
		Assert.assertEquals(EXPECTED_ROOT, root);
	}

	@Test
	public void testStart() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		IpfsFile root = _populateWithOneElement(access);
		Assert.assertEquals(6, access.data.size());
		Assert.assertEquals(0, access.pins.values().size());
		Assert.assertEquals(EXPECTED_ROOT, root);
		
		access.oneKey = MockKeys.K1;
		access.oneRoot = root;
		IpfsFile file = SimpleFolloweeStarter.startFollowingWithEmptyRecords((String message) -> {}, access, new CacheUpdater(null, null, null, null, null), MockKeys.K1);
		Assert.assertEquals(EXPECTED_FAKE, file);
		// We expect that an extra 2 elements were uploaded (the fake StreamRecords and the fake StreamIndex).
		Assert.assertEquals(8, access.data.size());
		// 3 of these are missing:  real StreamIndex, real StreamRecords, real StreamRecord.
		Assert.assertEquals(5, access.pins.values().size());
	}

	@Test(expected=KeyException.class)
	public void testFailedResolve() throws Throwable
	{
		MockWritingAccess access = new MockWritingAccess();
		access.oneKey = MockKeys.K1;
		SimpleFolloweeStarter.startFollowingWithEmptyRecords((String message) -> {}, access, null, MockKeys.K1);
		Assert.fail();
	}


	private static IpfsFile _populateWithOneElement(MockWritingAccess access) throws Throwable
	{
		String userPicCid = _storeWithString(access, "fake picture cid source".getBytes());
		StreamDescription desc = new StreamDescription();
		desc.setName("name");
		desc.setDescription("description");
		desc.setPicture(userPicCid);
		
		StreamRecord record = new StreamRecord();
		record.setName("post");
		record.setDescription("record description");
		record.setPublishedSecondsUtc(1L);
		record.setPublisherKey(MockKeys.K1.toPublicKey());
		record.setElements(new DataArray());
		
		StreamRecords records = new StreamRecords();
		records.getRecord().add(_storeWithString(access, GlobalData.serializeRecord(record)));
		
		StreamRecommendations recom = new StreamRecommendations();
		StreamIndex index = new StreamIndex();
		index.setVersion(1);
		index.setDescription(_storeWithString(access, GlobalData.serializeDescription(desc)));
		index.setRecords(_storeWithString(access, GlobalData.serializeRecords(records)));
		index.setRecommendations(_storeWithString(access, GlobalData.serializeRecommendations(recom)));
		return access.storeWithoutPin(GlobalData.serializeIndex(index));
	}

	private static String _storeWithString(MockWritingAccess access, byte[] data) throws Throwable
	{
		IpfsFile file = access.storeWithoutPin(data);
		return file.toSafeString();
	}
}
