package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.ExplicitCacheLogic;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns the stats around different Cacophony caches as a JSON struct:
 * -"followeeCacheBytes" (long)
 * -"explicitCacheBytes" (long)
 * -"favouritesCacheBytes" (long)
 */
public class GET_CacheStats implements ValidatedEntryPoints.GET
{
	private final Context _context;

	public GET_CacheStats(Context context
	)
	{
		_context = context;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		long explicitCacheBytes = ExplicitCacheLogic.getExplicitCacheSize(_context);
		try (IReadingAccess access = StandardAccess.readAccess(_context))
		{
			long followeeCacheBytes = CacheHelpers.getCurrentCacheSizeBytes(access.readableFolloweeData());
			long favouritesSizeBytes = access.readableFavouritesCache().getFavouritesSizeBytes();
			JsonObject stats = new JsonObject();
			stats.add("followeeCacheBytes", followeeCacheBytes);
			stats.add("explicitCacheBytes", explicitCacheBytes);
			stats.add("favouritesCacheBytes", favouritesSizeBytes);
			
			response.setContentType("application/json");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print(stats.toString());
		}
	}
}
