package com.jeffdisher.cacophony.interactive;

import java.io.IOException;
import java.io.PrintWriter;

import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Generates the generated_db.js (this is really just a stop-gap while we migrate into the dynamic interface).
 */
public class GET_GeneratedDb implements IGetHandler
{
	private final IEnvironment _environment;
	
	public GET_GeneratedDb(IEnvironment environment)
	{
		_environment = environment;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		// Since this is fetched immediately and is read-only, we can't reasonably check the XSRF.
		try
		{
			LocalConfig local = _environment.loadExistingConfig();
			LocalIndex localIndex = local.readLocalIndex();
			IConnection connection =  local.getSharedConnection();
			PrintWriter generatedStream = response.getWriter();
			INetworkScheduler scheduler = _environment.getSharedScheduler(connection, localIndex.keyName());
			LoadChecker checker = new LoadChecker(scheduler, local.loadGlobalPinCache(), connection);
			IpfsKey ourPublicKey = scheduler.getPublicKey();
			IpfsFile lastPublishedIndex = localIndex.lastPublishedIndex();
			GlobalPrefs prefs = local.readSharedPrefs();
			FollowIndex followIndex = local.loadFollowIndex();
			
			response.setContentType("text/javascript");
			response.setStatus(HttpServletResponse.SC_OK);
			String comment = "File is generated for the interactive --run method.";
			JsonGenerationHelpers.generateJsonDb(generatedStream, comment, checker, ourPublicKey, lastPublishedIndex, prefs, followIndex);
		}
		catch (VersionException e)
		{
			// This would just be an internal logic error, by this point.
			throw Assert.unexpected(e);
		}
		catch (UsageException e)
		{
			// This would just be an internal logic error, by this point.
			throw Assert.unexpected(e);
		}
		catch (IpfsConnectionException e)
		{
			// We may have already sent this header but there is no better plan, unfortunately.
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			response.getWriter().println(e.getLocalizedMessage());
		}
	}
}
