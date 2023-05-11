package com.jeffdisher.cacophony.commands;

import java.util.Iterator;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.HomeChannelModifier;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LeafFinder;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RemoveEntryFromThisChannelCommand(IpfsFile _elementCid) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException
	{
		if (null == _elementCid)
		{
			throw new UsageException("Element CID must be provided");
		}
		if (null == context.publicKey)
		{
			throw new UsageException("Channel must first be created with --createNewChannel");
		}
		
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			Assert.assertTrue(null != access.getLastRootElement());
			ILogger log = context.logger.logStart("Removing entry " + _elementCid + " from channel...");
			newRoot = _run(access, context.recordCache, _elementCid);
			if (null == newRoot)
			{
				throw new UsageException("Unknown post");
			}
			log.logFinish("Entry removed: " + _elementCid);
		}
		return new ChangedRoot(newRoot);
	}

	/**
	 * Removes the entry CID from the local user's stream.
	 * 
	 * @param access Write access.
	 * @param recordCache The record cache to update when removing (can be null).
	 * @param postToRemove The CID of the record to remove.
	 * @return The new local root element or null, if the entry wasn't found.
	 * @throws IpfsConnectionException There was a network error.
	 */
	private static IpfsFile _run(IWritingAccess access, LocalRecordCache recordCache, IpfsFile postToRemove) throws IpfsConnectionException
	{
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		StreamRecords records = modifier.loadRecords();
		
		boolean didRemove = false;
		Iterator<String> rawIterator = records.getRecord().iterator();
		while (rawIterator.hasNext())
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(rawIterator.next());
			if (postToRemove.equals(cid))
			{
				rawIterator.remove();
				Assert.assertTrue(!didRemove);
				didRemove = true;
			}
		}
		
		IpfsFile newRoot = null;
		if (didRemove)
		{
			// The ChannelModified updates the interior elements but not the leaf StreamRecord nodes or leaves.
			// This means we need to read the dead record from the network, and unpin it and any leaves, manually.
			// Start the read, do the update and commit new root before proceeding.
			FutureRead<StreamRecord> deadRecordFuture = access.loadCached(postToRemove, (byte[] data) -> GlobalData.deserializeRecord(data));
			
			// Update the channel structure, unpinning dropped data.
			modifier.storeRecords(records);
			newRoot = modifier.commitNewRoot();
			
			// Now, unpin this data and update the LocalRecordCache.
			StreamRecord deadRecord;
			try
			{
				deadRecord = deadRecordFuture.get();
			}
			catch (FailedDeserializationException e)
			{
				// This is for deserializing the local channel so the error isn't expected.
				throw Assert.unexpected(e);
			}
			
			// Interpret the leaves.
			if (null != recordCache)
			{
				LeafFinder leaves = LeafFinder.parseRecord(deadRecord);
				if (null != leaves.thumbnail)
				{
					recordCache.recordThumbnailReleased(postToRemove, leaves.thumbnail);
				}
				if (null != leaves.audio)
				{
					recordCache.recordAudioReleased(postToRemove, leaves.audio);
				}
				for (LeafFinder.VideoLeaf leaf : leaves.sortedVideos)
				{
					recordCache.recordVideoReleased(postToRemove, leaf.cid(), leaf.edgeSize());
				}
			}
			// Unpin everything, no matter what it means.
			for (DataElement leaf : deadRecord.getElements().getElement())
			{
				IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
				access.unpin(leafCid);
			}
			if (null != recordCache)
			{
				recordCache.recordMetaDataReleased(postToRemove);
			}
			access.unpin(postToRemove);
		}
		return newRoot;
	}
}
