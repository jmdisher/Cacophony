package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a list of post hashes for the given user, as a JSON array.
 * 
 * NOTE:  This is ONLY used by integration tests since they often want to see a snapshot of everything to prove what is
 * NOT known to the server.
 * Normal use-cases should instead listen to the WS_UserEntries event stream.
 */
public class GET_PostHashes implements ValidatedEntryPoints.GET
{
	private final Context _context;
	
	public GET_PostHashes(Context context
	)
	{
		_context = context;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable
	{
		IpfsKey userToResolve = IpfsKey.fromPublicKey((String)path[2]);
		try (IReadingAccess access = StandardAccess.readAccess(_context))
		{
			IpfsKey publicKey = _context.getSelectedKey();
			IpfsFile lastPublishedIndex = access.getLastRootElement();
			IFolloweeReading followees = access.readableFolloweeData();
			JsonArray hashes = JsonGenerationHelpers.postHashes(access, publicKey, lastPublishedIndex, followees, userToResolve);
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
	}
}
