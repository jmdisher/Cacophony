package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns miscellaneous information about the status of the server.
 * -"hash" (string) - the git hash of the current build
 * -"version" (string) - the human-readable version name of the current build
 * -"followeeCacheBytes" (long)
 * -"explicitCacheBytes" (long)
 * -"favouritesCacheBytes" (long)
 */
public class GET_ServerStatus implements ValidatedEntryPoints.GET
{
	private final Context _context;

	public GET_ServerStatus(Context context
	)
	{
		_context = context;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
	{
		JsonObject serverStatus = new JsonObject();
		
		JsonObject dataVersion = JsonGenerationHelpers.dataVersion();
		serverStatus.add("hash", dataVersion.get("hash"));
		serverStatus.add("version", dataVersion.get("version"));
		long explicitCacheBytes = _context.explicitCacheManager.getExplicitCacheSize();
		serverStatus.add("explicitCacheBytes", explicitCacheBytes);
		try (IReadingAccess access = StandardAccess.readAccess(_context))
		{
			long followeeCacheBytes = CacheHelpers.getCurrentCacheSizeBytes(access.readableFolloweeData());
			long favouritesSizeBytes = access.readableFavouritesCache().getFavouritesSizeBytes();
			serverStatus.add("followeeCacheBytes", followeeCacheBytes);
			serverStatus.add("favouritesCacheBytes", favouritesSizeBytes);
		}
		response.setContentType("application/json");
		response.setStatus(HttpServletResponse.SC_OK);
		response.getWriter().print(serverStatus.toString());
	}
}
