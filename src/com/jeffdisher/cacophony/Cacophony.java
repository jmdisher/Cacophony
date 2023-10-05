package com.jeffdisher.cacophony;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.function.LongSupplier;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.local.v4.DraftManager;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.ExplicitCacheManager;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IConnection;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Pair;
import com.jeffdisher.cacophony.utils.StandardLogger;
import com.jeffdisher.cacophony.utils.Version;


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

	// There is a very high degree of variability observed when requesting multiple pieces of remote data from the
	// network but this seems to be minimized with a larger number of threads so we use 16 instead of the earlier value
	// of 4.
	// This will likely still be tweaked in the future as more complex use-cases become common and can be tested.
	private static final int THREAD_COUNT = 16;

	/**
	 * The main entry-point for running the system.  Run without arguments to see the usage string.
	 * 
	 * @param args The command-line arguments.
	 */
	public static void main(String[] args)
	{
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
				String ipfsConnectString = System.getenv(EnvVars.ENV_VAR_CACOPHONY_IPFS_CONNECT);
				if (null == ipfsConnectString)
				{
					ipfsConnectString = DEFAULT_IPFS_CONNECT;
				}
				String keyName = CommandParser.getKeyNameFromEnv();
				
				// Make sure we get ownership of the lock file.
				MultiThreadedScheduler scheduler = null;
				boolean errorDidOccur = false;
				try (DataDomain.Lock lockFile = dataDirectoryWrapper.lock())
				{
					// We want to make sure that we can connect to the IPFS node.
					Pair<IConnection, URL> connectionData = dataDirectoryWrapper.buildSharedConnection(ipfsConnectString);
					IConnection connection = connectionData.first();
					
					// Create the scheduler we will use for the run.
					scheduler = new MultiThreadedScheduler(connection, THREAD_COUNT);
					
					// Make sure that the local storage is in a sane state and load it into memory.
					LocalDataModel localDataModel = LocalDataModel.verifiedAndLoadedModel(dataDirectoryWrapper.getFileSystem(), scheduler);
					
					// Create the executor and logger for our run and put them into the context.
					DraftManager draftManager = new DraftManager(dataDirectoryWrapper.getFileSystem().getDraftsTopLevelDirectory());
					boolean verbose = (null != System.getenv(EnvVars.ENV_VAR_CACOPHONY_VERBOSE));
					StandardLogger logger = StandardLogger.topLogger(System.out, verbose);
					URL baseUrl = connectionData.second();
					LongSupplier currentTimeMillisGenerator = () -> System.currentTimeMillis();
					// Create the default explicit cache manager (could be over-ridden by the interactive server, in its Context).
					// The default manager runs in synchronous mode, since that makes more sense for command-line usage.
					ExplicitCacheManager explicitCacheManager = new ExplicitCacheManager(localDataModel, connection, scheduler, logger, currentTimeMillisGenerator, false);
					Context context = _createContext(keyName
							, draftManager
							, localDataModel
							, connection
							, scheduler
							, logger
							, currentTimeMillisGenerator
							, explicitCacheManager
							, baseUrl
					);
					
					// Now, run the actual command (this normally returns soon but commands could be very long-running).
					ICommand.Result result = command.runInContext(context);
					
					// Shut down cache manager (this is a no-op, in this case).
					explicitCacheManager.shutdown();
					
					// Write the output to stdout.
					result.writeHumanReadable(System.out);
					
					boolean didPublish = _handleResult(context, result);
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
				catch (ProtocolDataException e)
				{
					System.err.println("Found malformed data on the network (" + e.getLocalizedMessage() + ").  The command did not complete.");
					System.exit(EXIT_COMPLETE_ERROR);
				}
				catch (CacophonyException e)
				{
					System.err.println("Exception encountered while running command: " + e.getLocalizedMessage());
					e.printStackTrace();
					System.exit(EXIT_COMPLETE_ERROR);
				}
				catch (IOException e)
				{
					System.err.println("Exception encountered accessing local storage: " + e.getLocalizedMessage());
					e.printStackTrace();
					System.exit(EXIT_COMPLETE_ERROR);
				}
				if (null != scheduler)
				{
					if (errorDidOccur)
					{
						// This is a "safe" error, meaning that the command completed successfully but some kind of clean-up may have failed, resulting in manual intervention steps being logged.
						System.exit(EXIT_SAFE_ERROR);
					}
					scheduler.shutdown();
				}
				dataDirectoryWrapper.close();
			}
			else if ((1 == args.length) && "--help".equals(args[0]))
			{
				// We handle "--help" as a special-case where we provide more than basic usage data but don't want to do the normal Cacophony start-up.
				_commonUsage(System.out);
				CommandParser.printHelp(System.out);
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

	private static Context _createContext(String keyName
			, DraftManager draftManager
			, LocalDataModel localDataModel
			, IConnection connection
			, INetworkScheduler scheduler
			, StandardLogger logger
			, LongSupplier currentTimeMillisGenerator
			, ExplicitCacheManager explicitCacheManager
			, URL baseUrl
	)
	{
		// We want to see if the key name we are working with is one which exists for us (could be null).
		IpfsKey selectedHomeKey = null;
		try (IReadOnlyLocalData reading = localDataModel.openForRead())
		{
			ChannelData channels = reading.readLocalIndex();
			selectedHomeKey = channels.getPublicKey(keyName);
		}
		
		CacheUpdater cacheUpdater = new CacheUpdater(null, null, null, null, null);
		Context context = new Context(draftManager
				, localDataModel
				, connection
				, scheduler
				, currentTimeMillisGenerator
				, logger
				, baseUrl
				, null
				, null
				, null
				, cacheUpdater
				, explicitCacheManager
				, selectedHomeKey
		);
		return context;
	}

	private static void errorStart()
	{
		_commonUsage(System.err);
		CommandParser.printUsage(System.err);
		System.err.println("More detailed usage can be seen with --help");
		System.exit(EXIT_STATIC_ERROR);
	}

	private static boolean _handleResult(Context context, ICommand.Result result)
	{
		boolean didPublish = false;
		// If there is a new root, publish it.
		IpfsFile newRoot = result.getIndexToPublish();
		if (null != newRoot)
		{
			try (IReadingAccess access = Context.readAccess(context))
			{
				context.logger.logVerbose("Publishing " + newRoot + "...");
				FuturePublish asyncPublish = access.beginIndexPublish(newRoot);
				
				// See if the publish actually succeeded (we still want to update our local state, even if it failed).
				CommandHelpers.commonWaitForPublish(context.logger, asyncPublish);
				didPublish = true;
			}
		}
		else
		{
			// We don't need to publish so we will say this was ok.
			didPublish = true;
		}
		return didPublish;
	}

	private static void _commonUsage(PrintStream stream)
	{
		stream.println("Cacophony release " + Version.TAG + " (build hash: " + Version.HASH + ")");
		stream.println("Usage:  Cacophony <command>");
		stream.println("\tStorage directory defaults to ~/.cacophony unless overridden with " + EnvVars.ENV_VAR_CACOPHONY_STORAGE + " env var");
	}
}
