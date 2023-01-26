package com.jeffdisher.cacophony.interactive;

import java.io.InputStream;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.logic.ChannelModifier;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.IpfsFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Used for updating the user info image for the home user.
 * Ideally, this would be combined with POST_Form_UserInfo but the current BreakWater multi-part implementation has size
 * limits so the image needs to be updated through this different path.
 */
public class POST_Raw_UserInfo implements ValidatedEntryPoints.POST_Raw
{
	private final IEnvironment _environment;
	private final BackgroundOperations _background;

	public POST_Raw_UserInfo(IEnvironment environment, BackgroundOperations background)
	{
		_environment = environment;
		_background = background;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		InputStream input = request.getInputStream();
		
		try (IWritingAccess access = StandardAccess.writeAccess(_environment))
		{
			// We will upload the image, even though it probably didn't change, but unpinning will at least balance the counting.
			IpfsFile cid = access.uploadAndPin(input);
			
			ChannelModifier modifier = new ChannelModifier(access);
			StreamDescription streamDescription = modifier.loadDescription();
			IpfsFile oldImage = IpfsFile.fromIpfsCid(streamDescription.getPicture());
			streamDescription.setPicture(cid.toSafeString());
			modifier.storeDescription(streamDescription);
			
			IpfsFile newRoot = modifier.commitNewRoot();
			access.unpin(oldImage);
			
			_background.requestPublish(newRoot);
			response.getWriter().print(access.getCachedUrl(cid));
			response.setStatus(HttpServletResponse.SC_OK);
		}
	}
}
