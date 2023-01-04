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
import com.jeffdisher.cacophony.logic.DraftWrapper;
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
		
		String forcedCommand = canChangeCommand
				? null
				: processingCommand
		;
		String xsrf = "XSRF_TOKEN_" + Math.random();
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(port, staticResource);
		server.addPostRawHandler("/cookie", 0, new POST_Raw_Cookie(xsrf));
		server.addPostRawHandler("/stop", 0, new POST_Raw_Stop(xsrf, stopLatch));
		server.addGetHandler("/videoConfig", 0, new GET_VideoConfig(xsrf, processingCommand, canChangeCommand));
		
		server.addGetHandler("/drafts", 0, new GET_Drafts(xsrf, manager));
		server.addPostRawHandler("/createDraft", 0, new POST_Raw_CreateDraft(xsrf, manager));
		server.addGetHandler("/draft", 1, new GET_Draft(xsrf, manager));
		server.addPostFormHandler("/draft", 1, new POST_Form_Draft(xsrf, manager));
		server.addDeleteHandler("/draft", 1, new DELETE_Draft(xsrf, manager));
		server.addPostRawHandler("/draft/publish", 2, new POST_Raw_DraftPublish(environment, xsrf, background, manager));
		server.addPostRawHandler("/wait/publish", 0, new POST_Raw_WaitPublish(environment, xsrf, background));
		
		server.addGetHandler("/draft/thumb", 1, new GET_DraftThumbnail(xsrf, manager));
		server.addPostRawHandler("/draft/thumb", 4, new POST_Raw_DraftThumb(xsrf, manager));
		server.addDeleteHandler("/draft/thumb", 1, new DELETE_DraftThumb(xsrf, manager));
		
		server.addGetHandler("/draft/originalVideo", 1, new GET_DraftLargeStream(xsrf, manager
				, (DraftWrapper wrapper) -> wrapper.originalVideo()
				, (Draft draft) -> draft.originalVideo().mime()
		));
		server.addGetHandler("/draft/processedVideo", 1, new GET_DraftLargeStream(xsrf, manager
				, (DraftWrapper wrapper) -> wrapper.processedVideo()
				, (Draft draft) -> draft.processedVideo().mime()
		));
		server.addGetHandler("/draft/audio", 1, new GET_DraftLargeStream(xsrf, manager
				, (DraftWrapper wrapper) -> wrapper.audio()
				, (Draft draft) -> draft.audio().mime()
		));
		
		server.addDeleteHandler("/draft/originalVideo", 1, new DELETE_DraftOriginalVideo(xsrf, manager));
		server.addDeleteHandler("/draft/processedVideo", 1, new DELETE_DraftProcessedVideo(xsrf, manager));
		server.addDeleteHandler("/draft/audio", 1, new DELETE_DraftAudio(xsrf, manager));
		
		server.addWebSocketFactory("/draft/saveVideo", 4, "video", new WS_DraftSaveVideo(xsrf, manager));
		server.addWebSocketFactory("/draft/processVideo", 2, "process", new WS_DraftProcessVideo(xsrf, manager, forcedCommand));
		server.addWebSocketFactory("/draft/saveAudio", 2, "audio", new WS_DraftSaveAudio(xsrf, manager));
		
		// We use a web socket for listening to updates of background process state.
		server.addWebSocketFactory("/backgroundStatus", 0, EVENT_API_PROTOCOL, new WS_BackgroundStatus(xsrf, statusHandoff));
		
		// Prefs.
		server.addGetHandler("/prefs", 0, new GET_Prefs(environment, xsrf));
		server.addPostFormHandler("/prefs", 0, new POST_Prefs(environment, xsrf, background));
		
		// General REST interface for read-only queries originally generated by the "--htmlOutput" mode.
		server.addGetHandler("/publicKey", 0, new GET_PublicKey(environment, xsrf));
		server.addGetHandler("/userInfo", 1, new GET_UserInfo(environment, xsrf));
		server.addGetHandler("/postHashes", 1, new GET_PostHashes(environment, xsrf));
		server.addGetHandler("/recommendedKeys", 1, new GET_RecommendedKeys(environment, xsrf));
		server.addGetHandler("/postStruct", 1, new GET_PostStruct(environment, xsrf));
		server.addGetHandler("/followeeKeys", 0, new GET_FolloweeKeys(environment, xsrf));
		server.addGetHandler("/version", 0, new GET_Version(xsrf));
		
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
