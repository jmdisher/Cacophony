package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.logic.HomeChannelModifier;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LeafFinder;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RemoveEntryFromThisChannelCommand(IpfsFile _elementCid) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		if (null == _elementCid)
		{
			throw new UsageException("Element CID must be provided");
		}
		IpfsKey userToEdit = context.getSelectedKey();
		if (null == userToEdit)
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
			// We want to remove this from anyone listening, if their is a listener registered.
			if (null != context.entryRegistry)
			{
				context.entryRegistry.removeLocalElement(userToEdit, _elementCid);
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
		AbstractRecords records = modifier.loadRecords();
		
		boolean didRemove = records.removeRecord(postToRemove);
		
		IpfsFile newRoot = null;
		if (didRemove)
		{
			// The ChannelModified updates the interior elements but not the leaf StreamRecord nodes or leaves.
			// This means we need to read the dead record from the network, and unpin it and any leaves, manually.
			// Start the read, do the update and commit new root before proceeding.
			FutureRead<AbstractRecord> deadRecordFuture = access.loadCached(postToRemove, AbstractRecord.DESERIALIZER);
			
			// Update the channel structure, unpinning dropped data.
			modifier.storeRecords(records);
			newRoot = modifier.commitNewRoot();
			
			// Now, unpin this data and update the LocalRecordCache.
			AbstractRecord deadRecord;
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
			if (null != deadRecord.getThumbnailCid())
			{
				access.unpin(deadRecord.getThumbnailCid());
			}
			if (null != deadRecord.getVideoExtension())
			{
				for (AbstractRecord.Leaf leaf : deadRecord.getVideoExtension())
				{
					IpfsFile leafCid = leaf.cid();
					access.unpin(leafCid);
				}
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
