package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.commands.ListChannelsCommand;
import com.jeffdisher.cacophony.commands.ListChannelsCommand.ChannelList;
import com.jeffdisher.cacophony.commands.ListChannelsCommand.OneChannel;
import com.jeffdisher.cacophony.interactive.InteractiveHelpers.SuccessfulCommand;
import com.jeffdisher.cacophony.scheduler.CommandRunner;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns the list of the home channels as a JSON array.  Each element contains:
 * -"keyName" (String) - the name of the channel
 * -"publicKey" (String) - the channel's public key
 * -"lastPublishedRoot" (String) - the IPFS CID of the last published channel root (StreamIndex)
 * -"isSelected" (boolean) - true if this is the currently selected channel
 * -"name" (String) - the name, cached from the description
 * -"userPicUrl" (String) - the picture URL, cached from the description
 */
public class GET_HomeChannels implements ValidatedEntryPoints.GET
{
	private final CommandRunner _runner;

	public GET_HomeChannels(CommandRunner runner
	)
	{
		_runner = runner;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		ListChannelsCommand command = new ListChannelsCommand();
		SuccessfulCommand<ChannelList> result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, null
				, command
				, null
		);
		if (null != result)
		{
			JsonArray array = new JsonArray();
			for(ListChannelsCommand.OneChannel channel : result.result().getChannels())
			{
				array.add(_asJson(channel));
			}
			response.setContentType("application/json");
			response.getWriter().print(array.toString());
		}
	}

	private JsonObject _asJson(OneChannel channel)
	{
		JsonObject object = new JsonObject();
		object.add("keyName", channel.keyName());
		object.add("publicKey", channel.publicKey().toPublicKey());
		object.add("lastPublishedRoot", channel.lastPublishedRoot().toSafeString());
		object.add("isSelected", channel.isSelected());
		object.add("name", channel.optionalName());
		object.add("userPicUrl", channel.optionalUserPicUrl());
		return object;
	}
}
