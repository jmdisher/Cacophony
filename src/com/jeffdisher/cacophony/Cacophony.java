package com.jeffdisher.cacophony;

import java.io.IOException;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.logic.StandardLogger;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IConnectionFactory;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


// XML Generation:  https://edwin.baculsoft.com/2019/11/java-generate-xml-from-xsd-using-xjc/
public class Cacophony {
	/**
	 * Exit code when there was a problem due to a static usage error.
	 */
	private static final int EXIT_STATIC_ERROR = 1;
	/**
	 * Exit code when there was a minor error which was ultimately mitigated.
	 */
	private static final int EXIT_SAFE_ERROR = 2;
	/**
	 * Exit code when there is a serious error which prevented the command from completing.
	 */
	private static final int EXIT_COMPLETE_ERROR = 3;

	/**
	 * The default IPFS API server connect string.
	 * This can be found in IPFS daemon startup output:  "API server listening on /ip4/127.0.0.1/tcp/5001".
	 */
	private static final String DEFAULT_IPFS_CONNECT = "/ip4/127.0.0.1/tcp/5001";

	/**
	 * Argument modes:
	 * "--createNewChannel" Used to create a new empty channel for this local key.
	 * "--updateDescription" Changes the description of the local channel.
	 * "--readDescription" Reads the description of the named public key (local channel if not provided), writing it to stdout.
	 * "--addRecommendation" Adds a new channel key to the recommended list from the local channel.
	 * "--removeRecommendation" Removes a channel key from the recommended list from the local channel.
	 * "--listRecommendations" Lists the recommended channel keys from the local channel to stdout.
	 * "--publishToThisChannel" Adds and publishes a new entry to the local channel.
	 * "--listChannel" Lists all the entries published to the named public key (local channel if not provided) to stdout.
	 * "--removeFromThisChannel" Removes a given entry from the local channel.
	 * 
	 * "--setGlobalPrefs":
	 *  "--cacheWidth" Sets the maximum video width to cache (smallest larger size will be chosen if a smaller one isn't available).
	 *  "--cacheHeight" Sets the maximum video width to cache (smallest larger size will be chosen if a smaller one isn't available).
	 *  "--cacheTotalBytes" Sets the maximum number of bytes to allow for automatic caching storage (only counts leaf data elements, not intermediary meta-data).
	 * "--updateNextFollowing" Does the polling cycle on the next channel being followed and advances polling state to the next.
	 * "--startFollowing" Adds the given channel ID to the following set.
	 * "--stopFollowing" Removes the given channel ID from the following set.
	 * "--readRemoteRecommendations" Reads the recommended channel IDs of the given ID and prints them to stdout.
	 * "--readRemoteChannel" Reads the contents of a given channel ID and prints it to stdout.
	 * "--favouriteElement" Adds the given element to the favourites pool (removing it from other pools if already present).
	 * "--unfavouriteElement" Removes the given element from the favourites pool.
	 * "--explicitLoadElement" Adds the given element to the explicit pool (ignoring this already present in another pool).
	 * "--readToLocalFile" Reads the default data from the given element into a file on the local filesystem (implicitly adding the element to the explicit pool)
	 * 
	 * @param args
	 * @throws IOException
	 * @throws JAXBException
	 * @throws SAXException
	 */
	public static void main(String[] args) throws IOException {
		if (args.length > 0)
		{
			ICommand<?> command = null;
			try
			{
				command = CommandParser.parseArgs(args, System.err);
			}
			catch (UsageException e)
			{
				System.err.println("Usage error in parsing command: " + e.getLocalizedMessage());
				System.exit(EXIT_STATIC_ERROR);
			}
			if (null != command)
			{
				DataDomain dataDirectoryWrapper = DataDomain.detectDataDomain();
				IConnectionFactory connectionFactory = dataDirectoryWrapper.getConnectionFactory();
				String ipfsConnectString = System.getenv(EnvVars.ENV_VAR_CACOPHONY_IPFS_CONNECT);
				if (null == ipfsConnectString)
				{
					ipfsConnectString = DEFAULT_IPFS_CONNECT;
				}
				String keyName = System.getenv(EnvVars.ENV_VAR_CACOPHONY_KEY_NAME);
				if (null == keyName)
				{
					keyName = CommandParser.DEFAULT_KEY_NAME;
				}
				
				// Make sure we get ownership of the lock file.
				StandardEnvironment executor = null;
				boolean errorDidOccur = false;
				try (DataDomain.Lock lockFile = dataDirectoryWrapper.lock())
				{
					IConnection connection = connectionFactory.buildConnection(ipfsConnectString);
					IpfsKey publicKey = _publicKeyForName(connection, keyName);
					executor = new StandardEnvironment(dataDirectoryWrapper.getFileSystem(), connection, keyName, publicKey);
					StandardLogger logger = StandardLogger.topLogger(System.out);
					// Make sure that we create an empty storage directory, if we don't already have one - we ignore whether or not this worked.
					StandardAccess.createNewChannelConfig(executor, ipfsConnectString, keyName);
					// Verify that the storage is consistent, before we start.
					executor.getSharedDataModel().verifyStorageConsistency();
					// Now, run the actual command (this normally returns soon but commands could be very long-running).
					ICommand.Result result = command.runInContext(new ICommand.Context(executor, logger));
					
					// Write the output to stdout.
					result.writeHumanReadable(System.out);
					
					boolean didPublish = _handleResult(executor, logger, result);
					if (!didPublish)
					{
						System.err.println("WARNING:  Update succeeded but publish failed so it will need to be retried");
					}
					errorDidOccur = logger.didErrorOccur();
				}
				catch (UsageException e)
				{
					System.err.println("Usage error in running command: " + e.getLocalizedMessage());
					System.exit(EXIT_STATIC_ERROR);
				}
				catch (IpfsConnectionException e)
				{
					System.err.println("Unexpected exception while contacting IPFS daemon (" + e.getLocalizedMessage() + ").  The command did not complete.");
					System.exit(EXIT_COMPLETE_ERROR);
				}
				catch (CacophonyException e)
				{
					System.err.println("Exception encountered while running command: " + e.getLocalizedMessage());
					e.printStackTrace();
					System.exit(EXIT_COMPLETE_ERROR);
				}
				if (null != executor)
				{
					if (errorDidOccur)
					{
						// This is a "safe" error, meaning that the command completed successfully but some kind of clean-up may have failed, resulting in manual intervention steps being logged.
						System.exit(EXIT_SAFE_ERROR);
					}
					executor.shutdown();
				}
				dataDirectoryWrapper.close();
			}
			else
			{
				errorStart();
			}
		}
		else
		{
			errorStart();
		}
	}

	private static void errorStart()
	{
		System.err.println("Cacophony release " + Version.TAG + " (build hash: " + Version.HASH + ")");
		System.err.println("Usage:  Cacophony <command>");
		System.err.println("\tStorage directory defaults to ~/.cacophony unless overridden with CACOPHONY_STORAGE env var");
		CommandParser.printUsage(System.err);
		System.exit(EXIT_STATIC_ERROR);
	}

	private static IpfsKey _publicKeyForName(IConnection ipfs, String keyName) throws IpfsConnectionException
	{
		IpfsKey publicKey = null;
		// First, see if the key exists.
		for (IConnection.Key info : ipfs.getKeys())
		{
			if (keyName.equals(info.name()))
			{
				Assert.assertTrue(null == publicKey);
				publicKey = info.key();
			}
		}
		// If not, create it now.
		if (null == publicKey)
		{
			publicKey = ipfs.generateKey(keyName).key();
		}
		Assert.assertTrue(null != publicKey);
		return publicKey;
	}

	private static boolean _handleResult(StandardEnvironment environment, StandardLogger logger, ICommand.Result result)
	{
		boolean didPublish = false;
		// If there is a new root, publish it.
		IpfsFile newRoot = result.getIndexToPublish();
		if (null != newRoot)
		{
			try (IWritingAccess access = StandardAccess.writeAccess(environment, logger))
			{
				logger.logVerbose("Publishing " + newRoot + "...");
				FuturePublish asyncPublish = access.beginIndexPublish(newRoot);
				
				// See if the publish actually succeeded (we still want to update our local state, even if it failed).
				CommandHelpers.commonWaitForPublish(logger, asyncPublish);
				didPublish = true;
			}
			catch (IpfsConnectionException e)
			{
				// If this happens, we will just report the warning that we couldn't publish.
			}
		}
		else
		{
			// We don't need to publish so we will say this was ok.
			didPublish = true;
		}
		return didPublish;
	}
}
