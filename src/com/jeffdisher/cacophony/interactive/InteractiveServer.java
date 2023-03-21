package com.jeffdisher.cacophony.interactive;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.eclipse.jetty.util.resource.Resource;

import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.IDraftWrapper;
import com.jeffdisher.cacophony.logic.IEnvironment;
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
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Helpers called by the RunCommand to setup and then handle the web requests.
 */
public class InteractiveServer
{
	// The common WebSocket protocol name for all front-end pages which use event_api.js.
	public  static final String EVENT_API_PROTOCOL = "event_api";

	public static void runServerUntilStop(IEnvironment environment, Resource staticResource, int port, String processingCommand, boolean canChangeCommand) throws IpfsConnectionException
	{
		System.out.println("Setting up initial state before starting server...");
		
		// Create the ConnectorDispatcher for our various HandoffConnector instances in the server.
		ConnectorDispatcher dispatcher = new ConnectorDispatcher();
		dispatcher.start();
		HandoffConnector<IpfsKey, Long> followeeRefreshConnector = new HandoffConnector<>(dispatcher);
		// We use a ConcurrentHashMap since the connectors can be mutated on different threads (start following while reading another user, etc).
		Map<IpfsKey, HandoffConnector<IpfsFile, Void>> connectorsPerUser = new ConcurrentHashMap<>();
		IpfsKey ourPublicKey;
		
		PrefsData prefs = null;
		IpfsFile rootElement = null;
		LocalRecordCache localRecordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			prefs = access.readPrefs();
			ourPublicKey = access.getPublicKey();
			rootElement = access.getLastRootElement();
			IFolloweeWriting followees = access.writableFolloweeData();
			followees.attachRefreshConnector(followeeRefreshConnector);
			
			try
			{
				_populateInitialHandoffs(access, connectorsPerUser, dispatcher, ourPublicKey, rootElement, followees);
			}
			catch (IpfsConnectionException e)
			{
				// This is a start-up failure.
				throw e;
			}
			catch (FailedDeserializationException e)
			{
				// We already cached this data so this shouldn't happen.
				throw Assert.unexpected(e);
			}
			LocalRecordCacheBuilder.populateInitialCacheForLocalUser(access, localRecordCache, userInfoCache, ourPublicKey, rootElement);
			LocalRecordCacheBuilder.populateInitialCacheForFollowees(access, localRecordCache, userInfoCache, followees);
		}
		
		// We will create a handoff connector for the status operations from the background operations.
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(dispatcher);
		// We need to create an instance of the shared BackgroundOperations (which will eventually move higher in the stack).
		BackgroundOperations background = new BackgroundOperations(environment, new BackgroundOperations.IOperationRunner()
		{
			@Override
			public FuturePublish startPublish(IpfsFile newRoot)
			{
				FuturePublish publish = null;
				try (IWritingAccess access = StandardAccess.writeAccess(environment))
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
			public Runnable startFolloweeRefresh(IpfsKey followeeKey)
			{
				ConcurrentFolloweeRefresher refresher = null;
				try (IWritingAccess access = StandardAccess.writeAccess(environment))
				{
					IFolloweeWriting followees = access.writableFolloweeData();
					IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(followeeKey);
					refresher = new ConcurrentFolloweeRefresher(environment
							, followeeKey
							, lastRoot
							, access.readPrefs()
							, false
					);
					refresher.setupRefresh(access, followees, ConcurrentFolloweeRefresher.EXISTING_FOLLOWEE_FULLNESS_FRACTION);
				}
				catch (IpfsConnectionException e)
				{
					// This case, we just log.
					// (we may want to re-request the publish attempt).
					environment.logError("Error in background refresh start: " + e.getLocalizedMessage());
				}
				// We must have a connector by this point since this is only called on refresh, not start follow.
				HandoffConnector<IpfsFile, Void> elementsUnknownForFollowee = connectorsPerUser.get(followeeKey);
				Assert.assertTrue(null != elementsUnknownForFollowee);
				return new RefreshWrapper(environment, localRecordCache, userInfoCache, refresher, elementsUnknownForFollowee);
			}
		}, statusHandoff, rootElement, prefs.republishIntervalMillis, prefs.followeeRefreshMillis);
		
		// Load all the known followees into the background operations for background refresh.
		try (IReadingAccess access = StandardAccess.readAccess(environment))
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
		
		String forcedCommand = canChangeCommand
				? null
				: processingCommand
		;
		String xsrf = "XSRF_TOKEN_" + Math.random();
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(port, staticResource);
		ValidatedEntryPoints validated = new ValidatedEntryPoints(server, xsrf);
		server.addPostRawHandler("/cookie", 0, new POST_Raw_Cookie(xsrf));
		validated.addGetHandler("/videoConfig", 0, new GET_VideoConfig(processingCommand, canChangeCommand));
		
		validated.addDeleteHandler("/post", 1, new DELETE_Post(environment, background, localRecordCache, connectorsPerUser.get(ourPublicKey)));
		validated.addGetHandler("/drafts", 0, new GET_Drafts(manager));
		validated.addPostRawHandler("/createDraft", 0, new POST_Raw_CreateDraft(environment, manager));
		validated.addGetHandler("/draft", 1, new GET_Draft(manager));
		validated.addPostFormHandler("/draft", 1, new POST_Form_Draft(manager));
		validated.addDeleteHandler("/draft", 1, new DELETE_Draft(manager));
		validated.addPostRawHandler("/draft/publish", 2, new POST_Raw_DraftPublish(environment, background, localRecordCache, manager, connectorsPerUser.get(ourPublicKey)));
		validated.addPostRawHandler("/wait/publish", 0, new POST_Raw_WaitPublish(environment, background));
		
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
		
		server.addWebSocketFactory("/draft/saveVideo", 4, "video", new WS_DraftSaveVideo(xsrf, manager));
		server.addWebSocketFactory("/draft/processVideo", 2, EVENT_API_PROTOCOL, new WS_DraftProcessVideo(xsrf, videoProcessContainer, forcedCommand));
		server.addWebSocketFactory("/draft/existingVideo", 1, EVENT_API_PROTOCOL, new WS_DraftExistingVideo(xsrf, videoProcessContainer));
		server.addWebSocketFactory("/draft/saveAudio", 2, "audio", new WS_DraftSaveAudio(xsrf, manager));
		
		// We use a web socket for listening to updates of background process state.
		server.addWebSocketFactory("/backgroundStatus", 0, EVENT_API_PROTOCOL, new WS_BackgroundStatus(environment, xsrf, statusHandoff, stopLatch, background));
		server.addWebSocketFactory("/followee/refreshTime", 0, EVENT_API_PROTOCOL, new WS_FolloweeRefreshTimes(xsrf, followeeRefreshConnector));
		server.addWebSocketFactory("/user/entries", 1, EVENT_API_PROTOCOL, new WS_UserEntries(xsrf, connectorsPerUser));
		
		// Prefs.
		validated.addGetHandler("/prefs", 0, new GET_Prefs(environment));
		validated.addPostFormHandler("/prefs", 0, new POST_Prefs(environment, background));
		
		// General data updates.
		validated.addPostRawHandler("/followees", 1, new POST_Raw_AddFollowee(environment, background, connectorsPerUser, dispatcher));
		validated.addDeleteHandler("/followees", 1, new DELETE_RemoveFollowee(environment, background, localRecordCache, userInfoCache, connectorsPerUser));
		validated.addPostRawHandler("/recommend", 1, new POST_Raw_AddRecommendation(environment, background));
		validated.addDeleteHandler("/recommend", 1, new DELETE_RemoveRecommendation(environment, background));
		validated.addPostFormHandler("/userInfo/info", 0, new POST_Form_UserInfo(environment, background, userInfoCache));
		validated.addPostRawHandler("/userInfo/image", 0, new POST_Raw_UserInfo(environment, background, userInfoCache));
		
		// Entry-points related to followee state changes.
		validated.addPostRawHandler("/followee/refresh", 1, new POST_Raw_FolloweeRefresh(background));
		
		// General REST interface for read-only queries originally generated by the "--htmlOutput" mode.
		validated.addGetHandler("/publicKey", 0, new GET_PublicKey(environment));
		validated.addGetHandler("/userInfo", 1, new GET_UserInfo(environment, userInfoCache));
		validated.addGetHandler("/postHashes", 1, new GET_PostHashes(environment));
		validated.addGetHandler("/recommendedKeys", 1, new GET_RecommendedKeys(environment));
		validated.addGetHandler("/postStruct", 1, new GET_PostStruct(environment, localRecordCache));
		validated.addGetHandler("/followeeKeys", 0, new GET_FolloweeKeys(environment));
		validated.addGetHandler("/version", 0, new GET_Version());
		
		// Special interface for requesting information about other users.
		// Note that we don't pin any of the information through this interface so it may fail or be slow.
		validated.addGetHandler("/unknownUser", 1, new GET_UnknownUserInfo(environment));
		
		server.start();
		if (null != forcedCommand)
		{
			System.out.println("Forced processing command: \"" + forcedCommand + "\"");
		}
		else
		{
			System.out.println("WARNING:  Dangerous processing mode enabled!  User will be able to control server-side command from front-end.");
		}
		System.out.println("Cacophony interactive server running: http://127.0.0.1:" + port);
		
		try
		{
			stopLatch.await();
		}
		catch (InterruptedException e)
		{
			// This thread isn't interrupted.
			throw Assert.unexpected(e);
		}
		System.out.println("Shutting down server...");
		server.stop();
		System.out.println("Shutting down connector dispatcher...");
		dispatcher.shutdown();
		System.out.println("Shutting down background process...");
		background.shutdownProcess();
		System.out.println("Background process shut down.");
	}


	private static void _populateInitialHandoffs(IReadingAccess access, Map<IpfsKey, HandoffConnector<IpfsFile, Void>> followeeConnectors, Consumer<Runnable> dispatcher, IpfsKey ourKey, IpfsFile ourRoot, IFolloweeReading followees) throws IpfsConnectionException, FailedDeserializationException
	{
		HandoffConnector<IpfsFile, Void> ourConnector = new HandoffConnector<>(dispatcher);
		followeeConnectors.put(ourKey, ourConnector);
		_populateConnector(access, ourConnector, ourRoot);
		for (IpfsKey followeeKey : followees.getAllKnownFollowees())
		{
			HandoffConnector<IpfsFile, Void> oneConnector = new HandoffConnector<>(dispatcher);
			followeeConnectors.put(followeeKey, oneConnector);
			IpfsFile oneRoot = followees.getLastFetchedRootForFollowee(followeeKey);
			_populateConnector(access, oneConnector, oneRoot);
		}
	}

	private static void _populateConnector(IReadingAccess access, HandoffConnector<IpfsFile, Void> connector, IpfsFile root) throws IpfsConnectionException, FailedDeserializationException
	{
		StreamIndex index = access.loadCached(root, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		StreamRecords records = access.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data)).get();
		for (String raw : records.getRecord())
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(raw);
			connector.create(cid, null);
		}
	}


	private static record RefreshWrapper(IEnvironment environment, LocalRecordCache localRecordCache, LocalUserInfoCache userInfoCache, ConcurrentFolloweeRefresher refresher, HandoffConnector<IpfsFile, Void> elementsUnknownForFollowee) implements Runnable
	{
		@Override
		public void run()
		{
			// Note that the "refresher" can be null if we were created after a connection error to the local daemon.
			// In that case, just log the problem.
			if (null != this.refresher)
			{
				Assert.assertTrue(null != elementsUnknownForFollowee);
				boolean didRefresh = this.refresher.runRefresh(elementsUnknownForFollowee);
				// Write-back any update associated with this (success or fail - since we want to update the time).
				try (IWritingAccess access = StandardAccess.writeAccess(environment))
				{
					IFolloweeWriting followees = access.writableFolloweeData();
					
					long lastPollMillis = environment.currentTimeMillis();
					this.refresher.finishRefresh(access, localRecordCache, userInfoCache, followees, lastPollMillis);
				}
				catch (IpfsConnectionException e)
				{
					// This case, we just log.
					environment.logError("Error in background refresh finish: " + e.getLocalizedMessage());
				}
				// (we just log the result)
				this.environment.logToConsole("Background refresh: " + (didRefresh ? "SUCCESS" : "FAILURE"));
			}
			else
			{
				this.environment.logToConsole("Background refresh skipped due to null refresher");
			}
		}
	}
}
