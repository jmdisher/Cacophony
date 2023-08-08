package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.commands.results.ChannelDescription;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.CidOrNone;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Just used to set the feature reference from the user description.
 * Can be an IpfsFile or "NONE".
 * Returns the updated info JSON on success.
 */
public class POST_Raw_Feature implements ValidatedEntryPoints.POST_Raw
{
	private final CommandRunner _runner;
	private final BackgroundOperations _background;

	public POST_Raw_Feature(CommandRunner runner
			, BackgroundOperations background
	)
	{
		_runner = runner;
		_background = background;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable
	{
		IpfsKey homePublicKey = IpfsKey.fromPublicKey((String)path[3]);
		CidOrNone featurePost = CidOrNone.parse((String)path[4]);
		UpdateDescriptionCommand command = new UpdateDescriptionCommand(null, null, null, null, null, featurePost);
		InteractiveHelpers.SuccessfulCommand<ChannelDescription> success = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, homePublicKey
				, command
				, homePublicKey
		);
		if (null != success)
		{
			ChannelDescription result = success.result();
			Context context = success.context();
			// Request the publication.
			_background.requestPublish(context.getSelectedKey(), result.getIndexToPublish());
			
			// We also want to write this back to the user info cache.
			IpfsKey key = context.getSelectedKey();
			context.userInfoCache.setUserInfo(key
					, result.name
					, result.description
					, result.userPicCid
					, result.email
					, result.website
					, result.feature
			);
			
			JsonObject userInfo = JsonGenerationHelpers.userDescription(result.name
					, result.description
					, result.userPicUrl
					, result.email
					, result.website
					, result.feature
			);
			response.setContentType("application/json");
			response.getWriter().print(userInfo.toString());
		}
	}
}
