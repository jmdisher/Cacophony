package com.jeffdisher.cacophony.interactive;

import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.util.resource.Resource;

import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Helpers called by the RunCommand to setup and then handle the web requests.
 */
public class InteractiveServer
{
	public static void runServerUntilStop(IEnvironment environment, Resource staticResource, int port, String processingCommand, boolean canChangeCommand) throws UsageException, VersionException, IpfsConnectionException
	{
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = local.readLocalIndex();
		IConnection connection =  local.getSharedConnection();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		DraftManager manager = local.buildDraftManager();
		IpfsKey ourPublicKey = scheduler.getPublicKey();
		
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
		server.addPostRawHandler("/draft/publish", 1, new POST_Raw_DraftPublish(environment, xsrf, manager));
		
		server.addGetHandler("/draft/thumb", 1, new GET_DraftThumbnail(xsrf, manager));
		server.addPostRawHandler("/draft/thumb", 4, new POST_Raw_DraftThumb(xsrf, manager));
		server.addDeleteHandler("/draft/thumb", 1, new DELETE_DraftThumb(xsrf, manager));
		
		server.addGetHandler("/draft/originalVideo", 1, new GET_DraftOriginalVideo(xsrf, manager));
		server.addGetHandler("/draft/processedVideo", 1, new GET_DraftProcessedVideo(xsrf, manager));
		
		server.addDeleteHandler("/draft/originalVideo", 1, new DELETE_DraftOriginalVideo(xsrf, manager));
		server.addDeleteHandler("/draft/processedVideo", 1, new DELETE_DraftProcessedVideo(xsrf, manager));
		
		server.addWebSocketFactory("/draft/saveVideo", 4, "video", new WS_DraftSaveVideo(xsrf, manager));
		server.addWebSocketFactory("/draft/processVideo", 2, "process", new WS_DraftProcessVideo(xsrf, manager, forcedCommand));
		
		// General REST interface for read-only queries originally generated by the "--htmlOutput" mode.
		server.addGetHandler("/publicKey", 0, new GET_PublicKey(xsrf, ourPublicKey));
		server.addGetHandler("/userInfo", 1, new GET_UserInfo(xsrf, scheduler, ourPublicKey, local));
		server.addGetHandler("/postHashes", 1, new GET_PostHashes(xsrf, scheduler, ourPublicKey, local));
		server.addGetHandler("/recommendedKeys", 1, new GET_RecommendedKeys(xsrf, scheduler, ourPublicKey, local));
		server.addGetHandler("/postStruct", 1, new GET_PostStruct(xsrf, scheduler, local));
		server.addGetHandler("/followeeKeys", 0, new GET_FolloweeKeys(xsrf, local));
		server.addGetHandler("/prefs", 0, new GET_Prefs(xsrf, local));
		server.addGetHandler("/version", 0, new GET_Version(xsrf));
		
		// Temporarily, we will just inject generation for the generated_db.js here.
		server.addGetHandler("/generated_db.js", 0, new GET_GeneratedDb(environment));
		
		
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
		server.stop();
	}
}
