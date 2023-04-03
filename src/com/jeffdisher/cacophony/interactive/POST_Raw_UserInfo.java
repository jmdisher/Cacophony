package com.jeffdisher.cacophony.interactive;

import java.io.InputStream;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.commands.results.ChannelDescription;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Used for updating the user info image for the home user.
 * Ideally, this would be combined with POST_Form_UserInfo but the current BreakWater multi-part implementation has size
 * limits so the image needs to be updated through this different path.
 */
public class POST_Raw_UserInfo implements ValidatedEntryPoints.POST_Raw
{
	private final ICommand.Context _context;
	private final BackgroundOperations _background;

	public POST_Raw_UserInfo(ICommand.Context context
			, BackgroundOperations background
	)
	{
		_context = context;
		_background = background;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		InputStream input = request.getInputStream();
		
		UpdateDescriptionCommand command = new UpdateDescriptionCommand(null, null, input, null, null);
		ChannelDescription result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _context
				, command
		);
		if (null != result)
		{
			// Request the publication.
			_background.requestPublish(result.getIndexToPublish());
			
			// We also want to write this back to the user info cache.
			IpfsKey key = _context.environment.getPublicKey();
			StreamDescription streamDescription = result.streamDescription;
			_context.userInfoCache.setUserInfo(key
					, streamDescription.getName()
					, streamDescription.getDescription()
					, IpfsFile.fromIpfsCid(streamDescription.getPicture())
					, streamDescription.getEmail()
					, streamDescription.getWebsite()
			);
			
			// Write out the uploaded file's URL.
			response.getWriter().print(result.userPicUrl);
		}
	}
}
