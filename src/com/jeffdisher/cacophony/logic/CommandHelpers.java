package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.MiscHelpers;


/**
 * This class contains miscellaneous common logic required by many of the commands.  There is no strong design to it
 * other than being entirely static.
 */
public class CommandHelpers
{
	/**
	 * Waits for the publish operation in-flight in asyncPublish to complete or fail, logging the result.
	 * 
	 * @param environment Used for logging.
	 * @param asyncPublish The in-flight asynchronous publication.
	 */
	public static void commonWaitForPublish(IEnvironment environment, FuturePublish asyncPublish)
	{
		StandardEnvironment.IOperationLog log = environment.logOperation("Waiting for publish " + asyncPublish.getIndexHash());
		IpfsConnectionException error = asyncPublish.get();
		if (null == error)
		{
			log.finish("Success!");
		}
		else
		{
			log.finish("Failed: " + error.getLocalizedMessage());
			environment.logError("WARNING:  Failed to publish new entry to IPNS (the post succeeded, but a republish will be required): " + asyncPublish.getIndexHash());
		}
	}

	/**
	 * Walks the local data to determine the current cache size.  If the size is greater than the target size in the
	 * prefs, it will unpin elements until it fits.
	 * 
	 * @param environment The execution environment.
	 * @param access Write access.
	 * @param fullnessFraction The fraction of the target we should consider our target (1.0 means full cache).
	 * @throws IpfsConnectionException If something goes wrong interacting with the IPFS node.
	 */
	public static void shrinkCacheToFitInPrefs(IEnvironment environment, IWritingAccess access, double fullnessFraction) throws IpfsConnectionException
	{
		IOperationLog log = environment.logOperation("Checking is cache requires shrinking...");
		IFolloweeWriting followees = access.writableFolloweeData();
		PrefsData prefs = access.readPrefs();
		
		{
			long currentCacheSizeBytes = CacheHelpers.getCurrentCacheSizeBytes(followees);
			long targetSizeBytes = (long)(prefs.followCacheTargetBytes * fullnessFraction);
			if (currentCacheSizeBytes > targetSizeBytes)
			{
				environment.logToConsole("Pruning cache to " + MiscHelpers.humanReadableBytes(targetSizeBytes) + " from current size of " + MiscHelpers.humanReadableBytes(currentCacheSizeBytes) + "...");
				long bytesToAdd = 0L;
				CacheHelpers.pruneCacheIfNeeded(access, followees, new CacheAlgorithm(targetSizeBytes, currentCacheSizeBytes), bytesToAdd);
			}
			else
			{
				environment.logToConsole("Not pruning cache since " + MiscHelpers.humanReadableBytes(currentCacheSizeBytes) + " is below target of " + MiscHelpers.humanReadableBytes(targetSizeBytes));
			}
		}
		log.finish("Cache clean finished without issue");
	}
}
