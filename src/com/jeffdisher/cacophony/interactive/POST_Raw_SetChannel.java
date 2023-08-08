package com.jeffdisher.cacophony.interactive;

import java.util.Set;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.projection.ChannelData;
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
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable
	{
		IpfsKey homePublicKey = IpfsKey.fromPublicKey((String)path[3]);
		
		// Check that this key exists (we need to use a low-level accessor since we might not currently have something selected).
		boolean didFind = false;
		LocalDataModel dataModel = _context.sharedDataModel;
		try (IReadOnlyLocalData reading = dataModel.openForRead())
		{
			ChannelData data = reading.readLocalIndex();
			Set<String> keyNames = data.getKeyNames();
			didFind = keyNames.stream().anyMatch((String keyName) -> homePublicKey.equals(data.getPublicKey(keyName)));
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
