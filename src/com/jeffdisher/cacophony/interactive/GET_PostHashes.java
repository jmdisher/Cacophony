package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a list of post hashes for the given user, as a JSON array.
 */
public class GET_PostHashes implements IGetHandler
{
	private final String _xsrf;
	private final LoadChecker _checker;
	private final IpfsKey _ourPublicKey;
	private final LocalConfig _localConfig;
	
	public GET_PostHashes(String xsrf, LoadChecker checker, IpfsKey ourPublicKey, LocalConfig localConfig)
	{
		_xsrf = xsrf;
		_checker = checker;
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
				IpfsFile lastPublishedIndex = _localConfig.readLocalIndex().lastPublishedIndex();
				FollowIndex followIndex = _localConfig.loadFollowIndex();
				JsonArray hashes = JsonGenerationHelpers.postHashes(_checker, _ourPublicKey, lastPublishedIndex, followIndex, userToResolve);
				if (null != hashes)
				{
					response.setContentType("application/json");
					response.setStatus(HttpServletResponse.SC_OK);
					response.getWriter().print(hashes.toString());
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
