package com.jeffdisher.cacophony;

import java.io.File;
import java.io.IOException;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.logic.RealConfigFileSystem;
import com.jeffdisher.cacophony.logic.RealConnectionFactory;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


// XML Generation:  https://edwin.baculsoft.com/2019/11/java-generate-xml-from-xsd-using-xjc/
public class Cacophony {
	public static final String ENV_VAR_CACOPHONY_STORAGE = "CACOPHONY_STORAGE";
	public static final String ENV_VAR_CACOPHONY_ENABLE_VERIFICATIONS = "CACOPHONY_ENABLE_VERIFICATIONS";

	public static final String DEFAULT_STORAGE_DIRECTORY_NAME = ".cacophony";

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
			ICommand command = null;
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
				File directory = _readCacophonyStorageDirectory();
				// Enable verifications if the env var is set, at all.
				boolean shouldEnableVerifications = (null != System.getenv(ENV_VAR_CACOPHONY_ENABLE_VERIFICATIONS));
				StandardEnvironment executor = new StandardEnvironment(System.out, new RealConfigFileSystem(directory), new RealConnectionFactory(), shouldEnableVerifications);
				try
				{
					command.runInEnvironment(executor);
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
				if (executor.didErrorOccur())
				{
					// This is a "safe" error, meaning that the command completed successfully but some kind of clean-up may have failed, resulting in manual intervention steps being logged.
					System.exit(EXIT_SAFE_ERROR);
				}
				executor.shutdown();
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

	private static File _readCacophonyStorageDirectory()
	{
		// Note that we default to "~/.cacophony" unless they provide the CACOPHONY_STORAGE environment variable.
		File storageDirectory = null;
		String envVarStorage = System.getenv(ENV_VAR_CACOPHONY_STORAGE);
		if (null != envVarStorage)
		{
			storageDirectory = new File(envVarStorage);
		}
		else
		{
			File homeDirectory = new File(System.getProperty("user.home"));
			Assert.assertTrue(homeDirectory.exists());
			storageDirectory = new File(homeDirectory, DEFAULT_STORAGE_DIRECTORY_NAME);
		}
		return storageDirectory;
	}
}
