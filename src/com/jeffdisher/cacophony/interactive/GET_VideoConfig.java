package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonObject;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a JSON struct of the video config options:
 * -height
 * -width
 * -fps
 * -videoBitrate
 * -audioBitrate
 * -processingCommand
 * -canChangeCommand
 */
public class GET_VideoConfig implements ValidatedEntryPoints.GET
{
	private final String _processingCommand;
	private final boolean _canChangeCommand;
	
	public GET_VideoConfig(String processingCommand, boolean canChangeCommand)
	{
		_processingCommand = processingCommand;
		_canChangeCommand = canChangeCommand;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
	{
		response.setContentType("application/json");
		response.setStatus(HttpServletResponse.SC_OK);
		
		JsonObject object = new JsonObject();
		// We will default to 480 with default FPS since this resolution is commonly supported but the framerate is finicky.
		object.set("height", 480);
		object.set("width", 640);
		object.set("fps", 30);
		// The browser tends to default to something like 2.5 Mbps for video and 128 kbps for audio but we will use a smaller number in case the user can't post-process (they can change this in the UI, themselves).
		object.set("videoBitrate", 256000);
		object.set("audioBitrate", 64000);
		object.set("processingCommand", _processingCommand);
		object.set("canChangeCommand", _canChangeCommand);
		response.getWriter().print(object.toString());
	}
}
