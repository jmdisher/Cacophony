package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.DataDomain;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.caches.EntryCacheRegistry;
import com.jeffdisher.cacophony.caches.LocalRecordCache;
import com.jeffdisher.cacophony.caches.LocalUserInfoCache;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecommendations;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.data.local.v4.IDataOpcode;
import com.jeffdisher.cacophony.data.local.v4.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v4.Opcode_SkipFolloweeRecord;
import com.jeffdisher.cacophony.logic.HandoffConnector.IHandoffListener;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.SilentLogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;


public class TestLocalRecordCacheBuilder
{
	@Test
	public void testSkippedFollowees() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode remoteConnection = new MockSingleNode(swarm);
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(null);
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(remoteConnection, 1);
		SilentLogger logger = new SilentLogger();
		
		IpfsFile recordFile = null;
		IpfsFile indexFile = null;
		Context context = new Context(null
				, model
				, remoteConnection
				, scheduler
				, () -> System.currentTimeMillis()
				, logger
				, DataDomain.FAKE_BASE_URL
				, null
				, null
				, null
				, null
				, null
				, null
		);
		
		// Store a channel along-with the information describing that we skipped an element.
		try (IWritingAccess access = StandardAccess.writeAccessWithKeyOverride(context.basicConnection, context.scheduler, context.logger, context.sharedDataModel, null, null))
		{
			recordFile = _storeEntry(access, "entry1", MockKeys.K1);
			indexFile = _storeNewIndex(access, recordFile);
			
			FolloweeData followIndex = access.writableFolloweeData();
			followIndex.createNewFollowee(MockKeys.K1, indexFile, 1L, 1L);
			followIndex.addSkippedRecord(MockKeys.K1, recordFile, false);
		}
		
		// Read the data and populate caches.
		LocalRecordCache recordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		EntryCacheRegistry entryRegistry = new EntryCacheRegistry((Runnable run) -> run.run());
		CacheUpdater cacheUpdater = new CacheUpdater(recordCache, userInfoCache, entryRegistry, null, null);
		try (IReadingAccess access = Context.readAccess(context))
		{
			IFolloweeReading followIndex = access.readableFolloweeData();
			LocalRecordCacheBuilder.populateInitialCacheForFollowees(access, cacheUpdater, followIndex);
		}
		entryRegistry.initializeCombinedView();
		
		// We should see this in the entry registry, since we know it exists, but not in the record cache since we don't know its content.
		BasicListener combined = new BasicListener();
		BasicListener one = new BasicListener();
		entryRegistry.getCombinedConnector().registerListener(combined, 10);
		entryRegistry.getReadOnlyConnector(MockKeys.K1).registerListener(one, 10);
		Assert.assertEquals(recordFile, combined.savedKey);
		Assert.assertEquals(recordFile, one.savedKey);
		Assert.assertEquals(0, recordCache.getKeys().size());
		scheduler.shutdown();
		
		// We also want to verify that the data still had the skipped record.
		int[] count = new int[1];
		try (InputStream stream = fileSystem.readAtomicFile("opcodes.v4.gzlog"))
		{
			OpcodeCodec.decodeStreamCustom(stream, (IDataOpcode opcode) -> {
				if (opcode instanceof Opcode_SkipFolloweeRecord)
				{
					count[0] += 1;
				}
			});
		}
		Assert.assertEquals(1, count[0]);
	}


	private static IpfsFile _storeNewIndex(IWritingAccess access, IpfsFile record) throws IpfsConnectionException, SizeConstraintException
	{
		AbstractRecords records = AbstractRecords.createNew();
		records.addRecord(record);
		byte[] data = records.serializeV2();
		IpfsFile recordsFile = access.uploadAndPin(new ByteArrayInputStream(data));
		
		AbstractRecommendations recommendations = AbstractRecommendations.createNew();
		data = recommendations.serializeV2();
		IpfsFile recommendationsFile = access.uploadAndPin(new ByteArrayInputStream(data));
		
		AbstractDescription description = AbstractDescription.createNew();
		description.setName("name");
		description.setDescription("description");
		data = description.serializeV2();
		IpfsFile descriptionFile = access.uploadAndPin(new ByteArrayInputStream(data));
		
		AbstractIndex index = AbstractIndex.createNew();
		index.descriptionCid = descriptionFile;
		index.recommendationsCid = recommendationsFile;
		index.recordsCid = recordsFile;
		return access.uploadAndPin(new ByteArrayInputStream(index.serializeV2()));
	}

	private static IpfsFile _storeEntry(IWritingAccess access, String name, IpfsKey publisher) throws IpfsConnectionException, SizeConstraintException
	{
		AbstractRecord record = AbstractRecord.createNew();
		record.setName(name);
		record.setPublishedSecondsUtc(1L);
		record.setPublisherKey(publisher);
		byte[] data = record.serializeV2();
		return access.uploadAndPin(new ByteArrayInputStream(data));
	}


	private static class BasicListener implements IHandoffListener<IpfsFile, Void>
	{
		// Our test only sees one of these.
		public IpfsFile savedKey;
		@Override
		public boolean create(IpfsFile key, Void value, boolean isNewest)
		{
			Assert.assertNull(this.savedKey);
			this.savedKey = key;
			return true;
		}
		@Override
		public boolean update(IpfsFile key, Void value)
		{
			throw new AssertionError("Not used in test");
		}
		@Override
		public boolean destroy(IpfsFile key)
		{
			throw new AssertionError("Not used in test");
		}
		@Override
		public boolean specialChanged(String special)
		{
			throw new AssertionError("Not used in test");
		}
	}
}
