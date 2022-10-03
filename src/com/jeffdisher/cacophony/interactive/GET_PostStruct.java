package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a single post as a JSON struct:
 * -cached (boolean)
 * -name (string)
 * -description (string)
 * -publishedSecondsUts (long)
 * -discussionUrl (string)
 * (if cached) -thumbnailUrl (string)
 * (if cached) -videoUrl (string)
 */
public class GET_PostStruct implements IGetHandler
{
	private final String _xsrf;
	private final INetworkScheduler _scheduler;
	private final LocalConfig _localConfig;
	
	public GET_PostStruct(String xsrf, INetworkScheduler scheduler, LocalConfig localConfig)
	{
		_xsrf = xsrf;
		_scheduler = scheduler;
		_localConfig = localConfig;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			IpfsFile postToResolve = IpfsFile.fromIpfsCid(variables[0]);
			try
			{
				IReadOnlyLocalData data = _localConfig.getSharedLocalData().openForRead();
				LoadChecker checker = new LoadChecker(_scheduler, data.readGlobalPinCache(), _localConfig.getSharedConnection());
				LocalIndex localIndex = data.readLocalIndex();
				FollowIndex followIndex = data.readFollowIndex();
				data.close();
				
				IpfsFile lastPublishedIndex = localIndex.lastPublishedIndex();
				LocalRecordCache cache = data.lazilyLoadFolloweeCache(() -> {
					try
					{
						return JsonGenerationHelpers.buildFolloweeCache(checker, lastPublishedIndex, followIndex);
					}
					catch (IpfsConnectionException e)
					{
						// We return null on error but log this.
						e.printStackTrace();
						return null;
					}
				});
				JsonObject postStruct = JsonGenerationHelpers.postStruct(cache, postToResolve);
				if (null != postStruct)
				{
					response.setContentType("application/json");
					response.setStatus(HttpServletResponse.SC_OK);
					response.getWriter().print(postStruct.toString());
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
