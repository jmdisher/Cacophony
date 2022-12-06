package com.jeffdisher.cacophony.commands;

import java.util.List;
import java.util.Map;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record ListCachedElementsForFolloweeCommand(IpfsKey _followeeKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		Assert.assertTrue(null != _followeeKey);
		
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			_runCore(environment, access);
		}
	}

	private void _runCore(IEnvironment environment, IReadingAccess access) throws IpfsConnectionException, UsageException, FailedDeserializationException
	{
		FollowRecord record = access.readOnlyFollowIndex().peekRecord(_followeeKey);
		if (null != record)
		{
			// We know that all the meta-data reachable from this root is cached locally, but not all the leaf data elements, so we will check the FollowRecord.
			Map<IpfsFile, FollowingCacheElement> cachedMapByElementCid = CacheHelpers.createCachedMap(record);
			IpfsFile root = record.lastFetchedRoot();
			
			StreamIndex index = access.loadCached(root, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
			StreamRecords records = access.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data)).get();
			List<String> recordList = records.getRecord();
			environment.logToConsole("Followee has " + recordList.size() + " elements");
			for(String elementCid : recordList)
			{
				FollowingCacheElement element = cachedMapByElementCid.get(IpfsFile.fromIpfsCid(elementCid));
				String suffix = null;
				if (null != element)
				{
					String imageString = (null != element.imageHash())
							? element.imageHash().toSafeString()
							: "(none)"
					;
					String leafString = (null != element.leafHash())
							? element.leafHash().toSafeString()
							: "(none)"
					;
					suffix = "(image: " + imageString + ", leaf: " + leafString + ")";
				}
				else
				{
					suffix = "(not cached)";
				}
				environment.logToConsole("Element CID: " + elementCid + " " + suffix);
			}
		}
		else
		{
			throw new UsageException("Not following " + _followeeKey.toPublicKey());
		}
	}
}
