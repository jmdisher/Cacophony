package com.jeffdisher.cacophony.interactive;

import java.util.Set;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.IFavouritesReading;
import com.jeffdisher.cacophony.types.IpfsFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a list of favourites, as a JSON array of the element hashes.
 */
public class GET_FavouritesHashes implements ValidatedEntryPoints.GET
{
	private final Context _context;

	public GET_FavouritesHashes(Context context
	)
	{
		_context = context;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		try (IReadingAccess access = StandardAccess.readAccess(_context))
		{
			JsonArray array = new JsonArray();
			IFavouritesReading favourites = access.readableFavouritesCache();
			Set<CachedRecordInfo> records = favourites.getRecords();
			for (CachedRecordInfo record : records)
			{
				IpfsFile recordCid = record.streamCid();
				array.add(recordCid.toSafeString());
			}
			response.setContentType("application/json");
			response.getWriter().print(array.toString());
		}
	}
}
