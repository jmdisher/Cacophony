package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Looks up description information for an unknown user, by key.
 * Returns build information as a JSON struct:
 * -name
 * -description
 * -userPicUrl
 * -email
 * -website
 */
public class GET_UnknownUserInfo implements ValidatedEntryPoints.GET
{
	private final IEnvironment _environment;
	
	public GET_UnknownUserInfo(IEnvironment environment)
	{
		_environment = environment;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		IpfsKey userToResolve = IpfsKey.fromPublicKey(variables[0]);
		if (null != userToResolve)
		{
			// Since every step in this operation can be blocking, but we only need network access, we just user small
			// read-only access requests.
			FutureResolve resolved;
			try (IReadingAccess access = StandardAccess.readAccess(_environment))
			{
				resolved = access.resolvePublicKey(userToResolve);
			}
			IpfsFile index = resolved.get();
			FutureRead<StreamIndex> futureStreamIndex;
			try (IReadingAccess access = StandardAccess.readAccess(_environment))
			{
				futureStreamIndex = access.loadNotCached(index, (byte[] data) -> GlobalData.deserializeIndex(data));
			}
			StreamIndex streamIndex = futureStreamIndex.get();
			IpfsFile description = IpfsFile.fromIpfsCid(streamIndex.getDescription());
			FutureRead<StreamDescription> futureStreamDescription;
			String directFetchUrlRoot;
			try (IReadingAccess access = StandardAccess.readAccess(_environment))
			{
				futureStreamDescription = access.loadNotCached(description, (byte[] data) -> GlobalData.deserializeDescription(data));
				directFetchUrlRoot = access.getDirectFetchUrlRoot();
			}
			StreamDescription streamDescription = futureStreamDescription.get();
			// Make sure that the picture is a valid CID before plumbing it through.
			IpfsFile pictureCid = IpfsFile.fromIpfsCid(streamDescription.getPicture());
			JsonObject userInfo = JsonGenerationHelpers.populateJsonForUnknownDescription(streamDescription, directFetchUrlRoot + pictureCid.toSafeString());
			response.setContentType("application/json");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print(userInfo.toString());
		}
		else
		{
			// This happens if the key is invalid.
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().print("Invalid key: " + variables[0]);
		}
	}
}
