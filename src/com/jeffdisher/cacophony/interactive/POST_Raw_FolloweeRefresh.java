package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Requests that the requested followee be polled for updates.  This is an asynchronous operation so it returns
 * immediately.  It will return a 404 if the followee is not known, otherwise 200.
 */
public class POST_Raw_FolloweeRefresh implements ValidatedEntryPoints.POST_Raw
{
	private final BackgroundOperations _background;
	
	public POST_Raw_FolloweeRefresh(BackgroundOperations background)
	{
		_background = background;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey userToRefresh = IpfsKey.fromPublicKey(pathVariables[0]);
		
		// The scheduler knows if this is a valid followee so it will tell us whether it will do anything.
		boolean didRequest = _background.refreshFollowee(userToRefresh);
		if (didRequest)
		{
			response.setStatus(HttpServletResponse.SC_OK);
		}
		else
		{
			// We don't know who this is.
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
	}
}
