package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Requests that currently selected home channel be changed to the one with the given public key.
 * Returns 200 on success (or if they already were selected), 400 if the key name is unknown.
 */
public class POST_Raw_SetChannel implements ValidatedEntryPoints.POST_Raw
{
	private final Context _context;

	public POST_Raw_SetChannel(Context context
	)
	{
		_context = context;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey homePublicKey = IpfsKey.fromPublicKey(pathVariables[0]);
		
		// Check that this key exists.
		boolean didFind = false;
		try (IReadingAccess access = StandardAccess.readAccess(_context))
		{
			for (IReadingAccess.HomeUserTuple tuple : access.readHomeUserData())
			{
				if (homePublicKey.equals(tuple.publicKey()))
				{
					didFind = true;
					break;
				}
			}
		}
		if (didFind)
		{
			_context.setSelectedKey(homePublicKey);
			response.setStatus(HttpServletResponse.SC_OK);
		}
		else
		{
			// This is unknown.
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
	}
}
