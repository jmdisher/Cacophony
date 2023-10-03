package com.jeffdisher.cacophony.interactive;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.util.resource.Resource;

import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.caches.EntryCacheRegistry;
import com.jeffdisher.cacophony.caches.HomeUserReplyCache;
import com.jeffdisher.cacophony.caches.LocalRecordCache;
import com.jeffdisher.cacophony.caches.LocalUserInfoCache;
import com.jeffdisher.cacophony.caches.ReplyForest;
import com.jeffdisher.cacophony.commands.CommandRunner;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.FutureCommand;
import com.jeffdisher.cacophony.commands.RefreshFolloweeCommand;
import com.jeffdisher.cacophony.commands.results.Incremental;
import com.jeffdisher.cacophony.data.local.v4.Draft;
import com.jeffdisher.cacophony.data.local.v4.DraftManager;
import com.jeffdisher.cacophony.data.local.v4.IDraftWrapper;
import com.jeffdisher.cacophony.logic.ExplicitCacheManager;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.LocalRecordCacheBuilder;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.ILogger;
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
	// The number of threads to use in the CommandRunner.  Most of these operations are simple but may block so we enable lots.
	public static final int COMMAND_RUNNER_THREAD_COUNT = 16;

	public static void runServerUntilStop(Context startingContext, Resource staticResource, int port, String processingCommand, boolean canChangeCommand) throws IpfsConnectionException
	{
		ILogger startupLogger = startingContext.logger.logStart("Setting up initial state before starting server...");
		long startMillis = startingContext.currentTimeMillisGenerator.getAsLong();
		
		// Create the ConnectorDispatcher for our various HandoffConnector instances in the server.
		ConnectorDispatcher dispatcher = new ConnectorDispatcher();
		dispatcher.start();
		HandoffConnector<IpfsKey, FolloweeData.TimePair> followeeRefreshConnector = new HandoffConnector<>(dispatcher);
		HandoffConnector<IpfsFile, IpfsFile> replyCacheConnector = new HandoffConnector<>(dispatcher);
		
		PrefsData prefs = null;
		LocalRecordCache localRecordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		HomeUserReplyCache replyCache = new HomeUserReplyCache(replyCacheConnector);
		EntryCacheRegistry entryRegistry = new EntryCacheRegistry(dispatcher);
		ReplyForest replyForest = new ReplyForest();
		CacheUpdater cacheUpdater = new CacheUpdater(localRecordCache, userInfoCache, entryRegistry, replyCache, replyForest);
		try (IWritingAccess access = Context.writeAccess(startingContext))
		{
			prefs = access.readPrefs();
			FolloweeData followees = access.writableFolloweeData();
			followees.attachRefreshConnector(followeeRefreshConnector);
			
			List<IReadingAccess.HomeUserTuple> homeTuples = access.readHomeUserData();
			
			// Note that we need to populate the record cache builder with home users before followees to make sure we can discover the replyTo relationships.
			for (IReadingAccess.HomeUserTuple tuple : homeTuples)
			{
				LocalRecordCacheBuilder.populateInitialCacheForLocalUser(access, cacheUpdater, tuple.publicKey(), tuple.lastRoot());
			}
			LocalRecordCacheBuilder.populateInitialCacheForFollowees(access, cacheUpdater, followees);
		}
		
		// Switch the entryRegistry into its normal "running" mode, now that bootstrap is completed.
		entryRegistry.initializeCombinedView();
		
		// Create the explicit cache manager in asynchronous mode.
		ExplicitCacheManager explicitCacheManager = new ExplicitCacheManager(startingContext.sharedDataModel
				, startingContext.basicConnection
				, startingContext.scheduler
				, startingContext.logger
				, startingContext.currentTimeMillisGenerator
				, true
		);
		
		// Create the context object which we will use for any command invocation from the interactive server.
		Context serverContext = startingContext.cloneWithExtras(localRecordCache, userInfoCache, entryRegistry, cacheUpdater, explicitCacheManager);
		CommandRunner runner = new CommandRunner(serverContext, COMMAND_RUNNER_THREAD_COUNT);
		
		// We will create a handoff connector for the status operations from the background operations.
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(dispatcher);
		// We need to create an instance of the shared BackgroundOperations (which will eventually move higher in the stack).
		BackgroundOperations background = new BackgroundOperations(serverContext.currentTimeMillisGenerator, serverContext.logger, new BackgroundOperations.IOperationRunner()
		{
			@Override
			public FuturePublish startPublish(String keyName, IpfsKey publicKey, IpfsFile newRoot)
			{
				// We need to fake-up a context since we are "acting as" the channel with the given keyName, not related to the selected channel.
				Context localContext = serverContext.cloneWithSelectedKey(publicKey);
				FuturePublish publish = null;
				try (IReadingAccess access = Context.readAccess(localContext))
				{
					publish = access.beginIndexPublish(newRoot);
				}
				return publish;
			}
			@Override
			public BackgroundOperations.OperationResult refreshFollowee(IpfsKey followeeKey)
			{
				// We just want to run the RefreshFolloweeCommand, since it internally does everything.
				RefreshFolloweeCommand command = new RefreshFolloweeCommand(followeeKey);
				FutureCommand<Incremental> result = runner.runBlockedCommand(followeeKey, command, null);
				BackgroundOperations.OperationResult operationResult;
				try
				{
					operationResult = result.get().moreToDo
							? BackgroundOperations.OperationResult.MORE_TO_DO
							: BackgroundOperations.OperationResult.SUCCESS
					;
				}
				catch (IpfsConnectionException e)
				{
					// This just means it didn't succeed due to some kind of network error.
					operationResult = BackgroundOperations.OperationResult.TEMPORARY_FAILURE;
				}
				catch (KeyException e)
				{
					// We couldn't look up the key - this is common since IPNS has a timeout.
					operationResult = BackgroundOperations.OperationResult.TEMPORARY_FAILURE;
				}
				catch (ProtocolDataException e)
				{
					// This means the user had corrupt data (we won't be able to refresh them until they fix it).
					operationResult = BackgroundOperations.OperationResult.TEMPORARY_FAILURE;
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
				return operationResult;
			}
		}, statusHandoff, prefs.republishIntervalMillis, prefs.followeeRefreshMillis);
		
		try (IReadingAccess access = Context.readAccess(serverContext))
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
		long endMillis = startingContext.currentTimeMillisGenerator.getAsLong();
		startupLogger.logFinish("Startup completed:  " + (endMillis - startMillis) + " ms");
		
		DraftManager manager = serverContext.sharedDraftManager;
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
		// We want to disable static resource caching.
		RestServer server = new RestServer(interfaceToBind, staticResource, "no-store,no-cache,must-revalidate");
		server.installPathParser("CID", (String raw) -> IpfsFile.fromIpfsCid(raw));
		server.installPathParser("KEY", (String raw) -> IpfsKey.fromPublicKey(raw));
		server.installPathParser("int", (String raw) -> Integer.parseInt(raw));
		ValidatedEntryPoints validated = new ValidatedEntryPoints(server, xsrf);
		
		// Entry-points for server-global.
		server.addPostRawHandler("/server/cookie", new POST_Raw_Cookie(xsrf));
		validated.addGetHandler("/server/status", new GET_ServerStatus(serverContext));
		validated.addPostRawHandler("/server/stop", new POST_Raw_Stop(stopLatch));
		validated.addGetHandler("/server/processingCommand", new GET_ProcessingCommand(processingCommand, canChangeCommand));
		validated.addGetHandler("/server/prefs", new GET_Prefs(serverContext));
		validated.addPostFormHandler("/server/prefs", new POST_Prefs(serverContext, background));
		validated.addWebSocketFactory("/server/events/status", EVENT_API_PROTOCOL, new WS_BackgroundStatus(statusHandoff));
		validated.addWebSocketFactory("/server/events/combined/entries", EVENT_API_PROTOCOL, new WS_CombinedEntries(serverContext));
		validated.addWebSocketFactory("/server/events/entries/{KEY}", EVENT_API_PROTOCOL, new WS_UserEntries(serverContext, dispatcher));
		validated.addWebSocketFactory("/server/events/replies", EVENT_API_PROTOCOL, new WS_Replies(replyCacheConnector));
		validated.addWebSocketFactory("/server/events/replyTree/{CID}", EVENT_API_PROTOCOL, new WS_ReplyTree(dispatcher, replyForest));
		validated.addGetHandler("/server/recommendedKeys/{KEY}", new GET_RecommendedKeys(runner));
		validated.addGetHandler("/server/postStruct/{CID}/{string}", new GET_PostStruct(runner));
		validated.addGetHandler("/server/unknownUser/{KEY}", new GET_UnknownUserInfo(runner));
		validated.addPostRawHandler("/server/clearExplicitCache", new POST_Raw_ClearExplicitCache(runner));
		
		// Home user operations.
		validated.addPostRawHandler("/home/republish/{KEY}", new POST_Raw_Republish(runner, background));
		validated.addPostFormHandler("/home/post/edit/{KEY}/{CID}", new POST_Form_EditPost(runner, background));
		validated.addDeleteHandler("/home/post/delete/{KEY}/{CID}", new DELETE_Post(runner, background));
		validated.addPostRawHandler("/home/recommend/add/{KEY}/{KEY}", new POST_Raw_AddRecommendation(runner, background));
		validated.addDeleteHandler("/home/recommend/remove/{KEY}/{KEY}", new DELETE_RemoveRecommendation(runner, background));
		validated.addPostFormHandler("/home/userInfo/info/{KEY}", new POST_Form_UserInfo(runner, background));
		validated.addPostRawHandler("/home/userInfo/image/{KEY}/{string}", new POST_Raw_UserInfo(runner, background));
		validated.addPostRawHandler("/home/userInfo/feature/{KEY}/{string}", new POST_Raw_Feature(runner, background));
		validated.addGetHandler("/home/channels", new GET_HomeChannels(runner));
		validated.addPostRawHandler("/home/channel/set/{KEY}", new POST_Raw_SetChannel(serverContext));
		validated.addPostRawHandler("/home/channel/new/{string}", new POST_Raw_NewChannel(serverContext, runner, background));
		validated.addDeleteHandler("/home/channel/delete/{KEY}", new DELETE_Channel(serverContext, runner, background));
		
		// Draft operations.
		validated.addGetHandler("/allDrafts/all", new GET_Drafts(manager));
		validated.addPostRawHandler("/allDrafts/new/{string}", new POST_Raw_CreateDraft(serverContext, manager));
		validated.addGetHandler("/draft/{int}", new GET_Draft(manager));
		validated.addPostFormHandler("/draft/{int}", new POST_Form_Draft(manager));
		validated.addDeleteHandler("/draft/{int}", new DELETE_Draft(manager));
		validated.addPostRawHandler("/draft/publish/{KEY}/{int}/{string}", new POST_Raw_DraftPublish(runner, background, manager));
		validated.addGetHandler("/draft/thumb/{int}", new GET_DraftThumbnail(manager));
		validated.addPostRawHandler("/draft/thumb/{int}/{int}/{int}/{string}", new POST_Raw_DraftThumb(manager));
		validated.addDeleteHandler("/draft/thumb/{int}", new DELETE_DraftThumb(manager));
		validated.addGetHandler("/draft/originalVideo/{int}", new GET_DraftLargeStream(manager
				, (IDraftWrapper wrapper) -> wrapper.readOriginalVideo()
				, (Draft draft) -> draft.originalVideo().mime()
				, (Draft draft) -> draft.originalVideo().byteSize()
		));
		validated.addWebSocketFactory("/draft/originalVideo/upload/{int}/{int}/{int}/{string}", "video", new WS_DraftSaveVideo(manager));
		validated.addDeleteHandler("/draft/originalVideo/{int}", new DELETE_DraftOriginalVideo(manager));
		validated.addGetHandler("/draft/processedVideo/{int}", new GET_DraftLargeStream(manager
				, (IDraftWrapper wrapper) -> wrapper.readProcessedVideo()
				, (Draft draft) -> draft.processedVideo().mime()
				, (Draft draft) -> draft.processedVideo().byteSize()
		));
		validated.addWebSocketFactory("/draft/processedVideo/process/{int}/{string}", EVENT_API_PROTOCOL, new WS_DraftProcessVideo(serverContext, videoProcessContainer, forcedCommand));
		validated.addWebSocketFactory("/draft/processedVideo/reconnect/{int}", EVENT_API_PROTOCOL, new WS_DraftExistingVideo(videoProcessContainer));
		validated.addDeleteHandler("/draft/processedVideo/{int}", new DELETE_DraftProcessedVideo(manager));
		validated.addGetHandler("/draft/audio/{int}", new GET_DraftLargeStream(manager
				, (IDraftWrapper wrapper) -> wrapper.readAudio()
				, (Draft draft) -> draft.audio().mime()
				, (Draft draft) -> draft.audio().byteSize()
		));
		validated.addWebSocketFactory("/draft/audio/upload/{int}/{string}", "audio", new WS_DraftSaveAudio(manager));
		validated.addDeleteHandler("/draft/audio/{int}", new DELETE_DraftAudio(manager));
		validated.addPostFormHandler("/quickReply/{KEY}/{CID}", new POST_Form_QuickReply(runner, background));
		
		// Followee operations.
		validated.addGetHandler("/followees/keys", new GET_FolloweeKeys(runner));
		validated.addPostRawHandler("/followees/add/{KEY}", new POST_Raw_AddFollowee(runner, background));
		validated.addDeleteHandler("/followees/remove/{KEY}", new DELETE_RemoveFollowee(runner, background));
		validated.addPostRawHandler("/followee/refresh/{KEY}", new POST_Raw_FolloweeRefresh(background));
		validated.addWebSocketFactory("/followee/events/refreshTime", EVENT_API_PROTOCOL, new WS_FolloweeRefreshTimes(followeeRefreshConnector));
		
		// Favourites operations.
		validated.addGetHandler("/favourites/list", new GET_FavouritesHashes(serverContext));
		validated.addPostRawHandler("/favourites/add/{CID}", new POST_Raw_AddFavourite(runner));
		validated.addDeleteHandler("/favourites/remove/{CID}", new DELETE_RemoveFavourite(runner));
		
		// Start the server.
		ILogger serverLog = serverContext.logger.logStart("Starting server...");
		server.start();
		if (null != forcedCommand)
		{
			serverLog.logOperation("Forced processing command: \"" + forcedCommand + "\"");
		}
		else
		{
			serverLog.logOperation("WARNING:  Dangerous processing mode enabled!  User will be able to control server-side command from front-end.  Default command is: \"" + processingCommand + "\"");
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
		// We want to shut-down the server components from the top-down, in order to avoid interruption exceptions from
		// Jetty (as we want to avoid our stack worrying about them just for this case).
		serverLog.logOperation("Received stop call.");
		// Some functionality sits on the background operations system but none of these other components.
		serverLog.logOperation("Shutting down background process...");
		background.shutdownProcess();
		// The command runner is what the background and most REST end-points use so that is next to stop.
		serverLog.logOperation("Shutting down command runner...");
		runner.shutdownThreads();
		// Explicit cache manager is used by commands, which are now shut down, so this can be stopped.
		serverLog.logOperation("Shutting down explicit cache manager...");
		explicitCacheManager.shutdown();
		// The connector dispatcher just responds to changes in explicit cache and commands so it can now come down.
		serverLog.logOperation("Shutting down connector dispatcher...");
		dispatcher.shutdown();
		// The server can now shut down since we shouldn't be blocked in any of our synchronization primitives.
		serverLog.logOperation("Shutting down Breakwater server...");
		server.stop();
		// The network scheduler is stopped in the top-level caller, and it is all that should remain.
		serverLog.logFinish("Shutdown complete.");
	}
}
