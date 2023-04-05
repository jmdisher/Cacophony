package com.jeffdisher.cacophony.interactive;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.util.resource.Resource;

import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.RefreshFolloweeCommand;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.ForeignChannelReader;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.IDraftWrapper;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.logic.LocalRecordCacheBuilder;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
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

	public static void runServerUntilStop(IEnvironment environment, ILogger logger, Resource staticResource, int port, String processingCommand, boolean canChangeCommand) throws IpfsConnectionException
	{
		logger.logVerbose("Setting up initial state before starting server...");
		
		// Create the ConnectorDispatcher for our various HandoffConnector instances in the server.
		ConnectorDispatcher dispatcher = new ConnectorDispatcher();
		dispatcher.start();
		HandoffConnector<IpfsKey, Long> followeeRefreshConnector = new HandoffConnector<>(dispatcher);
		IpfsKey ourPublicKey;
		
		PrefsData prefs = null;
		IpfsFile rootElement = null;
		LocalRecordCache localRecordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		EntryCacheRegistry entryRegistry;
		try (IWritingAccess access = StandardAccess.writeAccess(environment, logger))
		{
			prefs = access.readPrefs();
			ourPublicKey = access.getPublicKey();
			rootElement = access.getLastRootElement();
			IFolloweeWriting followees = access.writableFolloweeData();
			followees.attachRefreshConnector(followeeRefreshConnector);
			EntryCacheRegistry.Builder entryRegistryBuilder = new EntryCacheRegistry.Builder(dispatcher, PER_USER_COMBINED_START_SIZE);
			
			try
			{
				_populateInitialHandoffs(access, entryRegistryBuilder, ourPublicKey, rootElement, followees);
			}
			catch (IpfsConnectionException e)
			{
				// This is a start-up failure.
				throw e;
			}
			LocalRecordCacheBuilder.populateInitialCacheForLocalUser(access, localRecordCache, userInfoCache, ourPublicKey, rootElement);
			LocalRecordCacheBuilder.populateInitialCacheForFollowees(access, localRecordCache, userInfoCache, followees);
			entryRegistry = entryRegistryBuilder.buildRegistry(ourPublicKey
					, (IpfsFile elementHash) -> access.loadCached(elementHash, (byte[] data) -> GlobalData.deserializeRecord(data))
			);
		}
		
		// Create the context object which we will use for any command invocation from the interactive server.
		ICommand.Context serverContext = new ICommand.Context(environment, logger, localRecordCache, userInfoCache, entryRegistry);
		
		// We will create a handoff connector for the status operations from the background operations.
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(dispatcher);
		// We need to create an instance of the shared BackgroundOperations (which will eventually move higher in the stack).
		BackgroundOperations background = new BackgroundOperations(environment, logger, new BackgroundOperations.IOperationRunner()
		{
			@Override
			public FuturePublish startPublish(IpfsFile newRoot)
			{
				FuturePublish publish = null;
				try (IWritingAccess access = StandardAccess.writeAccess(environment, logger))
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
				boolean didRefresh;
				try
				{
					command.runInContext(serverContext);
					didRefresh = true;
				}
				catch (IpfsConnectionException e)
				{
					// This just means it didn't succeed (but is harmless - usually means we couldn't look up the key).
					didRefresh = false;
				}
				catch (UsageException e)
				{
					// This shouldn't happen (we are calling it from an internal utility) but we may want to make sure
					// that this can't happen as a result of some kind of race.
					throw Assert.unexpected(e);
				}
				return didRefresh;
			}
		}, statusHandoff, rootElement, prefs.republishIntervalMillis, prefs.followeeRefreshMillis);
		
		// Load all the known followees into the background operations for background refresh.
		try (IReadingAccess access = StandardAccess.readAccess(environment, logger))
		{
			// We will also just request an update of every followee we have.
			IFolloweeReading followees = access.readableFolloweeData();
			for (IpfsKey followeeKey : followees.getAllKnownFollowees())
			{
				background.enqueueFolloweeRefresh(followeeKey, followees.getLastPollMillisForFollowee(followeeKey));
			}
		}
		background.startProcess();
		
		DraftManager manager = environment.getSharedDraftManager();
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
		
		// Install the entry-points.
		server.addPostRawHandler("/cookie", 0, new POST_Raw_Cookie(xsrf));
		validated.addGetHandler("/videoConfig", 0, new GET_VideoConfig(processingCommand, canChangeCommand));
		
		validated.addDeleteHandler("/post", 1, new DELETE_Post(serverContext, background));
		validated.addGetHandler("/drafts", 0, new GET_Drafts(manager));
		validated.addPostRawHandler("/createDraft", 0, new POST_Raw_CreateDraft(serverContext, manager));
		validated.addGetHandler("/draft", 1, new GET_Draft(manager));
		validated.addPostFormHandler("/draft", 1, new POST_Form_Draft(manager));
		validated.addDeleteHandler("/draft", 1, new DELETE_Draft(manager));
		validated.addPostRawHandler("/draft/publish", 2, new POST_Raw_DraftPublish(serverContext, background, manager));
		validated.addPostRawHandler("/wait/publish", 0, new POST_Raw_WaitPublish(serverContext, background));
		
		validated.addGetHandler("/draft/thumb", 1, new GET_DraftThumbnail(manager));
		validated.addPostRawHandler("/draft/thumb", 4, new POST_Raw_DraftThumb(manager));
		validated.addDeleteHandler("/draft/thumb", 1, new DELETE_DraftThumb(manager));
		
		validated.addGetHandler("/draft/originalVideo", 1, new GET_DraftLargeStream(manager
				, (IDraftWrapper wrapper) -> wrapper.readOriginalVideo()
				, (Draft draft) -> draft.originalVideo().mime()
				, (Draft draft) -> draft.originalVideo().byteSize()
		));
		validated.addGetHandler("/draft/processedVideo", 1, new GET_DraftLargeStream(manager
				, (IDraftWrapper wrapper) -> wrapper.readProcessedVideo()
				, (Draft draft) -> draft.processedVideo().mime()
				, (Draft draft) -> draft.processedVideo().byteSize()
		));
		validated.addGetHandler("/draft/audio", 1, new GET_DraftLargeStream(manager
				, (IDraftWrapper wrapper) -> wrapper.readAudio()
				, (Draft draft) -> draft.audio().mime()
				, (Draft draft) -> draft.audio().byteSize()
		));
		
		validated.addDeleteHandler("/draft/originalVideo", 1, new DELETE_DraftOriginalVideo(manager));
		validated.addDeleteHandler("/draft/processedVideo", 1, new DELETE_DraftProcessedVideo(manager));
		validated.addDeleteHandler("/draft/audio", 1, new DELETE_DraftAudio(manager));
		
		validated.addWebSocketFactory("/draft/saveVideo", 4, "video", new WS_DraftSaveVideo(manager));
		validated.addWebSocketFactory("/draft/processVideo", 2, EVENT_API_PROTOCOL, new WS_DraftProcessVideo(videoProcessContainer, forcedCommand));
		validated.addWebSocketFactory("/draft/existingVideo", 1, EVENT_API_PROTOCOL, new WS_DraftExistingVideo(videoProcessContainer));
		validated.addWebSocketFactory("/draft/saveAudio", 2, "audio", new WS_DraftSaveAudio(manager));
		
		// We use a web socket for listening to updates of background process state.
		validated.addWebSocketFactory("/backgroundStatus", 0, EVENT_API_PROTOCOL, new WS_BackgroundStatus(serverContext, statusHandoff, stopLatch, background));
		validated.addWebSocketFactory("/followee/refreshTime", 0, EVENT_API_PROTOCOL, new WS_FolloweeRefreshTimes(followeeRefreshConnector));
		validated.addWebSocketFactory("/user/entries", 1, EVENT_API_PROTOCOL, new WS_UserEntries(serverContext));
		validated.addWebSocketFactory("/combined/entries", 0, EVENT_API_PROTOCOL, new WS_CombinedEntries(serverContext));
		
		// Prefs.
		validated.addGetHandler("/prefs", 0, new GET_Prefs(serverContext));
		validated.addPostFormHandler("/prefs", 0, new POST_Prefs(serverContext, background));
		
		// General data updates.
		validated.addPostRawHandler("/followees", 1, new POST_Raw_AddFollowee(serverContext, background));
		validated.addDeleteHandler("/followees", 1, new DELETE_RemoveFollowee(serverContext, background));
		validated.addPostRawHandler("/recommend", 1, new POST_Raw_AddRecommendation(serverContext, background));
		validated.addDeleteHandler("/recommend", 1, new DELETE_RemoveRecommendation(serverContext, background));
		validated.addPostFormHandler("/userInfo/info", 0, new POST_Form_UserInfo(serverContext, background));
		validated.addPostRawHandler("/userInfo/image", 0, new POST_Raw_UserInfo(serverContext, background));
		validated.addPostFormHandler("/editPost", 1, new POST_Form_EditPost(serverContext, background));
		
		// Entry-points related to followee state changes.
		validated.addPostRawHandler("/followee/refresh", 1, new POST_Raw_FolloweeRefresh(background));
		
		// General REST interface for read-only queries originally generated by the "--htmlOutput" mode.
		validated.addGetHandler("/publicKey", 0, new GET_PublicKey(serverContext));
		validated.addGetHandler("/userInfo", 1, new GET_UserInfo(serverContext));
		validated.addGetHandler("/postHashes", 1, new GET_PostHashes(serverContext));
		validated.addGetHandler("/recommendedKeys", 1, new GET_RecommendedKeys(serverContext));
		validated.addGetHandler("/postStruct", 1, new GET_PostStruct(serverContext));
		validated.addGetHandler("/followeeKeys", 0, new GET_FolloweeKeys(serverContext));
		validated.addGetHandler("/version", 0, new GET_Version());
		
		// Special interface for requesting information about other users.
		// Note that we don't pin any of the information through this interface so it may fail or be slow.
		validated.addGetHandler("/unknownUser", 1, new GET_UnknownUserInfo(serverContext));
		
		// Start the server.
		ILogger serverLog = logger.logStart("Starting server...");
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
		background.shutdownProcess();
		serverLog.logFinish("Background process shut down.");
	}


	private static void _populateInitialHandoffs(IReadingAccess access, EntryCacheRegistry.Builder entryRegistryBuilder, IpfsKey ourKey, IpfsFile ourRoot, IFolloweeReading followees) throws IpfsConnectionException
	{
		entryRegistryBuilder.createConnector(ourKey);
		_populateConnector(access, entryRegistryBuilder, ourKey, ourRoot);
		for (IpfsKey followeeKey : followees.getAllKnownFollowees())
		{
			entryRegistryBuilder.createConnector(followeeKey);
			IpfsFile oneRoot = followees.getLastFetchedRootForFollowee(followeeKey);
			_populateConnector(access, entryRegistryBuilder, followeeKey, oneRoot);
		}
	}

	private static void _populateConnector(IReadingAccess access, EntryCacheRegistry.Builder entryRegistryBuilder, IpfsKey key, IpfsFile root) throws IpfsConnectionException
	{
		ForeignChannelReader reader = new ForeignChannelReader(access, root, true);
		StreamRecords records;
		try
		{
			records = reader.loadRecords();
		}
		catch (FailedDeserializationException e)
		{
			// We should not have already cached this if it was corrupt.
			throw Assert.unexpected(e);
		}
		catch (SizeConstraintException e)
		{
			// We should not have already cached this if it was too big.
			throw Assert.unexpected(e);
		}
		for (String raw : records.getRecord())
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(raw);
			entryRegistryBuilder.addToUser(key, cid);
		}
	}
}
