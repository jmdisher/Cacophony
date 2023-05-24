package com.jeffdisher.cacophony.interactive;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.util.resource.Resource;

import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.RefreshFolloweeCommand;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.ForeignChannelReader;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.IDraftWrapper;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.logic.LocalRecordCacheBuilder;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.scheduler.FutureCommand;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Helpers called by the RunCommand to setup and then handle the web requests.
 */
public class InteractiveServer
{
	// The common WebSocket protocol name for all front-end pages which use event_api.js.
	public  static final String EVENT_API_PROTOCOL = "event_api";
	// The number of most recent entries, for each user, which will be added to the combined list.
	public static final int PER_USER_COMBINED_START_SIZE = 10;
	// The number of threads to use in the CommandRunner.
	public static final int COMMAND_RUNNER_THREAD_COUNT = 4;

	public static void runServerUntilStop(Context startingContext, Resource staticResource, int port, String processingCommand, boolean canChangeCommand) throws IpfsConnectionException
	{
		startingContext.logger.logVerbose("Setting up initial state before starting server...");
		
		// Create the ConnectorDispatcher for our various HandoffConnector instances in the server.
		ConnectorDispatcher dispatcher = new ConnectorDispatcher();
		dispatcher.start();
		HandoffConnector<IpfsKey, Long> followeeRefreshConnector = new HandoffConnector<>(dispatcher);
		
		PrefsData prefs = null;
		LocalRecordCache localRecordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		EntryCacheRegistry entryRegistry;
		try (IWritingAccess access = StandardAccess.writeAccess(startingContext))
		{
			prefs = access.readPrefs();
			IFolloweeWriting followees = access.writableFolloweeData();
			followees.attachRefreshConnector(followeeRefreshConnector);
			EntryCacheRegistry.Builder entryRegistryBuilder = new EntryCacheRegistry.Builder(dispatcher, PER_USER_COMBINED_START_SIZE);
			
			List<IReadingAccess.HomeUserTuple> homeTuples = access.readHomeUserData();
			try
			{
				for (IReadingAccess.HomeUserTuple tuple : homeTuples)
				{
					entryRegistryBuilder.createConnector(tuple.publicKey());
					_populateConnector(access, entryRegistryBuilder, tuple.publicKey(), tuple.lastRoot());
				}
				for (IpfsKey followeeKey : followees.getAllKnownFollowees())
				{
					entryRegistryBuilder.createConnector(followeeKey);
					IpfsFile oneRoot = followees.getLastFetchedRootForFollowee(followeeKey);
					_populateConnector(access, entryRegistryBuilder, followeeKey, oneRoot);
				}
			}
			catch (IpfsConnectionException e)
			{
				// This is a start-up failure.
				throw e;
			}
			for (IReadingAccess.HomeUserTuple tuple : homeTuples)
			{
				LocalRecordCacheBuilder.populateInitialCacheForLocalUser(access, localRecordCache, userInfoCache, tuple.publicKey(), tuple.lastRoot());
			}
			LocalRecordCacheBuilder.populateInitialCacheForFollowees(access, localRecordCache, userInfoCache, followees);
			entryRegistry = entryRegistryBuilder.buildRegistry(
					(IpfsFile elementHash) -> access.loadCached(elementHash, (byte[] data) -> GlobalData.deserializeRecord(data))
			);
		}
		
		// Create the context object which we will use for any command invocation from the interactive server.
		Context serverContext = startingContext.cloneWithExtras(localRecordCache, userInfoCache, entryRegistry);
		CommandRunner runner = new CommandRunner(serverContext, COMMAND_RUNNER_THREAD_COUNT);
		
		// We will create a handoff connector for the status operations from the background operations.
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(dispatcher);
		// We need to create an instance of the shared BackgroundOperations (which will eventually move higher in the stack).
		BackgroundOperations background = new BackgroundOperations(serverContext.environment, serverContext.logger, new BackgroundOperations.IOperationRunner()
		{
			@Override
			public FuturePublish startPublish(String keyName, IpfsKey publicKey, IpfsFile newRoot)
			{
				// We need to fake-up a context since we are "acting as" the channel with the given keyName, not related to the selected channel.
				Context localContext = serverContext.cloneWithSelectedKey(publicKey);
				FuturePublish publish = null;
				try (IReadingAccess access = StandardAccess.readAccess(localContext))
				{
					publish = access.beginIndexPublish(newRoot);
				}
				catch (IpfsConnectionException e)
				{
					// We want to push this error through to the finish by synthesizing a publish.
					publish = new FuturePublish(newRoot);
					publish.failure(e);
				}
				return publish;
			}
			@Override
			public boolean refreshFollowee(IpfsKey followeeKey)
			{
				// We just want to run the RefreshFolloweeCommand, since it internally does everything.
				RefreshFolloweeCommand command = new RefreshFolloweeCommand(followeeKey);
				FutureCommand<None> result = runner.runBlockedCommand(followeeKey, command, null);
				boolean didRefresh;
				try
				{
					result.get();
					didRefresh = true;
				}
				catch (IpfsConnectionException e)
				{
					// This just means it didn't succeed due to some kind of network error.
					didRefresh = false;
				}
				catch (KeyException e)
				{
					// We couldn't look up the key - this is common since IPNS has a timeout.
					didRefresh = false;
				}
				catch (ProtocolDataException e)
				{
					// This means the user had corrupt data (we won't be able to refresh them until they fix it).
					didRefresh = false;
				}
				catch (UsageException e)
				{
					// This shouldn't happen (we are calling it from an internal utility) but we may want to make sure
					// that this can't happen as a result of some kind of race.
					throw Assert.unexpected(e);
				}
				catch (CacophonyException e)
				{
					// A more specific exception would be caught, above.
					throw Assert.unexpected(e);
				}
				return didRefresh;
			}
		}, statusHandoff, prefs.republishIntervalMillis, prefs.followeeRefreshMillis);
		
		try (IReadingAccess access = StandardAccess.readAccess(serverContext))
		{
			// Load all the local channels.
			List<IReadingAccess.HomeUserTuple> homeTuples = access.readHomeUserData();
			for (IReadingAccess.HomeUserTuple tuple : homeTuples)
			{
				background.addChannel(tuple.keyName(), tuple.publicKey(), tuple.lastRoot());
			}
			
			// Load all the known followees into the background operations for background refresh.
			// We will also just request an update of every followee we have.
			IFolloweeReading followees = access.readableFolloweeData();
			for (IpfsKey followeeKey : followees.getAllKnownFollowees())
			{
				background.enqueueFolloweeRefresh(followeeKey, followees.getLastPollMillisForFollowee(followeeKey));
			}
		}
		background.startProcess();
		runner.startThreads();
		
		DraftManager manager = serverContext.environment.getSharedDraftManager();
		HandoffConnector<String, Long> videoProcessingConnector = new HandoffConnector<>(dispatcher);
		VideoProcessContainer videoProcessContainer = new VideoProcessContainer(manager, videoProcessingConnector);
		
		// Prepare the initial known values we need.
		String forcedCommand = canChangeCommand
				? null
				: processingCommand
		;
		String xsrf = "XSRF_TOKEN_" + Math.random();
		
		// Setup the server.
		CountDownLatch stopLatch = new CountDownLatch(1);
		// Note that we specifically want the 127.0.0.1 interface since this should only be used locally and this IP is a special-case for browser permissions.
		InetSocketAddress interfaceToBind = new InetSocketAddress("127.0.0.1", port);
		RestServer server = new RestServer(interfaceToBind, staticResource);
		ValidatedEntryPoints validated = new ValidatedEntryPoints(server, xsrf);
		
		// Entry-points for server-global.
		server.addPostRawHandler("/server/cookie", 0, new POST_Raw_Cookie(xsrf));
		validated.addGetHandler("/server/version", 0, new GET_Version());
		validated.addPostRawHandler("/server/stop", 0, new POST_Raw_Stop(stopLatch));
		validated.addGetHandler("/server/videoConfig", 0, new GET_VideoConfig(processingCommand, canChangeCommand));
		validated.addGetHandler("/server/prefs", 0, new GET_Prefs(serverContext));
		validated.addPostFormHandler("/server/prefs", 0, new POST_Prefs(serverContext, background));
		validated.addWebSocketFactory("/server/events/status", 0, EVENT_API_PROTOCOL, new WS_BackgroundStatus(statusHandoff));
		validated.addWebSocketFactory("/server/events/combined/entries", 0, EVENT_API_PROTOCOL, new WS_CombinedEntries(serverContext));
		validated.addWebSocketFactory("/server/events/entries", 1, EVENT_API_PROTOCOL, new WS_UserEntries(serverContext));
		validated.addGetHandler("/server/postHashes", 1, new GET_PostHashes(serverContext));
		validated.addGetHandler("/server/recommendedKeys", 1, new GET_RecommendedKeys(runner));
		validated.addGetHandler("/server/postStruct", 1, new GET_PostStruct(runner));
		validated.addGetHandler("/server/unknownUser", 1, new GET_UnknownUserInfo(runner));
		validated.addGetHandler("/server/userInfo", 1, new GET_UserInfo(serverContext));
		
		// Home user operations.
		validated.addPostRawHandler("/home/republish", 1, new POST_Raw_Republish(runner, background));
		validated.addPostFormHandler("/home/post/edit", 2, new POST_Form_EditPost(runner, background));
		validated.addDeleteHandler("/home/post/delete", 2, new DELETE_Post(runner, background));
		validated.addPostRawHandler("/home/recommend/add", 2, new POST_Raw_AddRecommendation(runner, background));
		validated.addDeleteHandler("/home/recommend/remove", 2, new DELETE_RemoveRecommendation(runner, background));
		validated.addPostFormHandler("/home/userInfo/info", 1, new POST_Form_UserInfo(runner, background));
		validated.addPostRawHandler("/home/userInfo/image", 1, new POST_Raw_UserInfo(runner, background));
		validated.addGetHandler("/home/publicKey", 0, new GET_PublicKey(serverContext));
		validated.addGetHandler("/home/channels", 0, new GET_HomeChannels(runner));
		validated.addPostRawHandler("/home/channel/set", 1, new POST_Raw_SetChannel(serverContext));
		validated.addPostRawHandler("/home/channel/new", 1, new POST_Raw_NewChannel(serverContext, runner, background));
		
		// Draft operations.
		validated.addGetHandler("/allDrafts/all", 0, new GET_Drafts(manager));
		validated.addPostRawHandler("/allDrafts/new", 0, new POST_Raw_CreateDraft(serverContext, manager));
		validated.addGetHandler("/draft", 1, new GET_Draft(manager));
		validated.addPostFormHandler("/draft", 1, new POST_Form_Draft(manager));
		validated.addDeleteHandler("/draft", 1, new DELETE_Draft(manager));
		validated.addPostRawHandler("/draft/publish", 3, new POST_Raw_DraftPublish(serverContext, background, manager));
		validated.addPostRawHandler("/draft/waitPublish", 0, new POST_Raw_WaitPublish(serverContext, background));
		validated.addGetHandler("/draft/thumb", 1, new GET_DraftThumbnail(manager));
		validated.addPostRawHandler("/draft/thumb", 4, new POST_Raw_DraftThumb(manager));
		validated.addDeleteHandler("/draft/thumb", 1, new DELETE_DraftThumb(manager));
		validated.addGetHandler("/draft/originalVideo", 1, new GET_DraftLargeStream(manager
				, (IDraftWrapper wrapper) -> wrapper.readOriginalVideo()
				, (Draft draft) -> draft.originalVideo().mime()
				, (Draft draft) -> draft.originalVideo().byteSize()
		));
		validated.addWebSocketFactory("/draft/originalVideo/upload", 4, "video", new WS_DraftSaveVideo(manager));
		validated.addDeleteHandler("/draft/originalVideo", 1, new DELETE_DraftOriginalVideo(manager));
		validated.addGetHandler("/draft/processedVideo", 1, new GET_DraftLargeStream(manager
				, (IDraftWrapper wrapper) -> wrapper.readProcessedVideo()
				, (Draft draft) -> draft.processedVideo().mime()
				, (Draft draft) -> draft.processedVideo().byteSize()
		));
		validated.addWebSocketFactory("/draft/processedVideo/process", 2, EVENT_API_PROTOCOL, new WS_DraftProcessVideo(serverContext, videoProcessContainer, forcedCommand));
		validated.addWebSocketFactory("/draft/processedVideo/reconnect", 1, EVENT_API_PROTOCOL, new WS_DraftExistingVideo(videoProcessContainer));
		validated.addDeleteHandler("/draft/processedVideo", 1, new DELETE_DraftProcessedVideo(manager));
		validated.addGetHandler("/draft/audio", 1, new GET_DraftLargeStream(manager
				, (IDraftWrapper wrapper) -> wrapper.readAudio()
				, (Draft draft) -> draft.audio().mime()
				, (Draft draft) -> draft.audio().byteSize()
		));
		validated.addWebSocketFactory("/draft/audio/upload", 2, "audio", new WS_DraftSaveAudio(manager));
		validated.addDeleteHandler("/draft/audio", 1, new DELETE_DraftAudio(manager));
		
		// Followee operations.
		validated.addGetHandler("/followees/keys", 0, new GET_FolloweeKeys(runner));
		validated.addPostRawHandler("/followees/add", 1, new POST_Raw_AddFollowee(runner, background));
		validated.addDeleteHandler("/followees/remove", 1, new DELETE_RemoveFollowee(runner, background));
		validated.addPostRawHandler("/followee/refresh", 1, new POST_Raw_FolloweeRefresh(background));
		validated.addWebSocketFactory("/followee/events/refreshTime", 0, EVENT_API_PROTOCOL, new WS_FolloweeRefreshTimes(followeeRefreshConnector));
		
		// Start the server.
		ILogger serverLog = serverContext.logger.logStart("Starting server...");
		server.start();
		if (null != forcedCommand)
		{
			serverLog.logOperation("Forced processing command: \"" + forcedCommand + "\"");
		}
		else
		{
			serverLog.logOperation("WARNING:  Dangerous processing mode enabled!  User will be able to control server-side command from front-end.");
		}
		serverLog.logOperation("Cacophony interactive server running: http://127.0.0.1:" + port);
		
		try
		{
			stopLatch.await();
		}
		catch (InterruptedException e)
		{
			// This thread isn't interrupted.
			throw Assert.unexpected(e);
		}
		serverLog.logOperation("Shutting down server...");
		server.stop();
		serverLog.logOperation("Shutting down connector dispatcher...");
		dispatcher.shutdown();
		serverLog.logOperation("Shutting down background process...");
		runner.shutdownThreads();
		background.shutdownProcess();
		serverLog.logFinish("Background process shut down.");
	}


	private static void _populateConnector(IReadingAccess access, EntryCacheRegistry.Builder entryRegistryBuilder, IpfsKey key, IpfsFile root) throws IpfsConnectionException
	{
		ForeignChannelReader reader = new ForeignChannelReader(access, root, true);
		StreamRecords records;
		try
		{
			records = reader.loadRecords();
		}
		catch (ProtocolDataException e)
		{
			// We should not have already cached this if it was corrupt.
			throw Assert.unexpected(e);
		}
		for (String raw : records.getRecord())
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(raw);
			entryRegistryBuilder.addToUser(key, cid);
		}
	}
}
