package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a list of recommended public keys for the given user, as a JSON array.
 */
public class GET_RecommendedKeys implements IGetHandler
{
	private final String _xsrf;
	private final INetworkScheduler _scheduler;
	private final IpfsKey _ourPublicKey;
	private final LocalConfig _localConfig;
	
	public GET_RecommendedKeys(String xsrf, INetworkScheduler scheduler, IpfsKey ourPublicKey, LocalConfig localConfig)
	{
		_xsrf = xsrf;
		_scheduler = scheduler;
		_ourPublicKey = ourPublicKey;
		_localConfig = localConfig;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			IpfsKey userToResolve = IpfsKey.fromPublicKey(variables[0]);
			try
			{
				LoadChecker checker = new LoadChecker(_scheduler, _localConfig.loadGlobalPinCache(), _localConfig.getSharedConnection());
				LocalIndex localIndex = _localConfig.readLocalIndex();
				FollowIndex followIndex = _localConfig.loadFollowIndex();
				
				IpfsFile lastPublishedIndex = localIndex.lastPublishedIndex();
				JsonArray keys = JsonGenerationHelpers.recommendedKeys(checker, _ourPublicKey, lastPublishedIndex, followIndex, userToResolve);
				if (null != keys)
				{
					response.setContentType("application/json");
					response.setStatus(HttpServletResponse.SC_OK);
					response.getWriter().print(keys.toString());
				}
				else
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
			catch (IpfsConnectionException e)
			{
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				e.printStackTrace(response.getWriter());
			}
		}
	}
}
