package com.jeffdisher.cacophony.interactive;

import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.util.resource.Resource;

import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IDraftWrapper;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Helpers called by the RunCommand to setup and then handle the web requests.
 */
public class InteractiveServer
{
	// The common WebSocket protocol name for all front-end pages which use event_api.js.
	public  static final String EVENT_API_PROTOCOL = "event_api";

	public static void runServerUntilStop(IEnvironment environment, Resource staticResource, int port, String processingCommand, boolean canChangeCommand) throws UsageException, VersionException, IpfsConnectionException
	{
		PrefsData prefs = null;
		IpfsFile rootElement = null;
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			prefs = access.readPrefs();
			rootElement = access.getLastRootElement();
		}
		
		// We will create a handoff connector for the status operations from the background operations.
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>();
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
					// This case, we just log.
					// (we may want to re-request the publish attempt).
					environment.logError("Error in background publish: " + e.getLocalizedMessage());
				}
				catch (UsageException | VersionException e)
				{
					// We don't expect these by this point.
					throw Assert.unexpected(e);
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
					refresher = new ConcurrentFolloweeRefresher(environment, followeeKey, lastRoot, access.readPrefs(), false);
					refresher.setupRefresh(access, followees, ConcurrentFolloweeRefresher.EXISTING_FOLLOWEE_FULLNESS_FRACTION);
				}
				catch (IpfsConnectionException e)
				{
					// This case, we just log.
					// (we may want to re-request the publish attempt).
					environment.logError("Error in background refresh start: " + e.getLocalizedMessage());
				}
				catch (UsageException | VersionException e)
				{
					// We don't expect these by this point.
					throw Assert.unexpected(e);
				}
				return new RefreshWrapper(refresher);
			}
			@Override
			public void finishFolloweeRefresh(Runnable refresher)
			{
				try (IWritingAccess access = StandardAccess.writeAccess(environment))
				{
					IFolloweeWriting followees = access.writableFolloweeData();
					
					long lastPollMillis = System.currentTimeMillis();
					((RefreshWrapper)refresher).refresher.finishRefresh(access, followees, lastPollMillis);
				}
				catch (IpfsConnectionException e)
				{
					// This case, we just log.
					// (we may want to re-request the publish attempt).
					environment.logError("Error in background refresh finish: " + e.getLocalizedMessage());
				}
				catch (UsageException | VersionException e)
				{
					// We don't expect these by this point.
					throw Assert.unexpected(e);
				}
			}
			@Override
			public long currentTimeMillis()
			{
				return System.currentTimeMillis();
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
		VideoProcessContainer videoProcessContainer = new VideoProcessContainer(manager);
		
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
		
		validated.addGetHandler("/drafts", 0, new GET_Drafts(manager));
		validated.addPostRawHandler("/createDraft", 0, new POST_Raw_CreateDraft(manager));
		validated.addGetHandler("/draft", 1, new GET_Draft(manager));
		validated.addPostFormHandler("/draft", 1, new POST_Form_Draft(manager));
		validated.addDeleteHandler("/draft", 1, new DELETE_Draft(manager));
		validated.addPostRawHandler("/draft/publish", 2, new POST_Raw_DraftPublish(environment, background, manager));
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
		
		// Prefs.
		validated.addGetHandler("/prefs", 0, new GET_Prefs(environment));
		validated.addPostFormHandler("/prefs", 0, new POST_Prefs(environment, background));
		
		// General REST interface for read-only queries originally generated by the "--htmlOutput" mode.
		validated.addGetHandler("/publicKey", 0, new GET_PublicKey(environment));
		validated.addGetHandler("/userInfo", 1, new GET_UserInfo(environment));
		validated.addGetHandler("/postHashes", 1, new GET_PostHashes(environment));
		validated.addGetHandler("/recommendedKeys", 1, new GET_RecommendedKeys(environment));
		validated.addGetHandler("/postStruct", 1, new GET_PostStruct(environment));
		validated.addGetHandler("/followeeKeys", 0, new GET_FolloweeKeys(environment));
		validated.addGetHandler("/version", 0, new GET_Version());
		
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
		System.out.println("Server shut down.  Shutting down background process...");
		background.shutdownProcess();
		System.out.println("Background process shut down.");
	}


	private static record RefreshWrapper(ConcurrentFolloweeRefresher refresher) implements Runnable
	{
		@Override
		public void run()
		{
			boolean didRefresh = this.refresher.runRefresh();
			// (we just log the result)
			System.out.println("Background refresh: " + didRefresh);
		}
	}
}
