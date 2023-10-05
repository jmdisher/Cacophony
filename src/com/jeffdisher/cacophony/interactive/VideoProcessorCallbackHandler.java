package com.jeffdisher.cacophony.interactive;

import org.eclipse.jetty.websocket.api.Session;

import com.eclipsesource.json.Json;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The callbacks we use for video processing are byte sizes keyed by information we are reporting to the front-end, as
 * defined in VideoProcessContainer.
 */
public class VideoProcessorCallbackHandler implements HandoffConnector.IHandoffListener<String, Long>
{
	private final Session _session;

	/**
	 * Creates the handler on top of the given WebSocket session.
	 * 
	 * @param session The session of the WebSocket to use.
	 */
	public VideoProcessorCallbackHandler(Session session)
	{
		_session = session;
	}

	@Override
	public boolean create(String key, Long value, boolean isNewest)
	{
		return SocketEventHelpers.sendCreate(_session.getRemote(), Json.value(key), Json.value(value.longValue()), isNewest);
	}

	@Override
	public boolean update(String key, Long value)
	{
		return SocketEventHelpers.sendUpdate(_session.getRemote(), Json.value(key), Json.value(value.longValue()));
	}

	@Override
	public boolean destroy(String key)
	{
		boolean didSend = SocketEventHelpers.sendDelete(_session.getRemote(), Json.value(key));
		// If we are destroying the key related to final output, we are ready to close this socket.
		if (didSend && (key.equals(VideoProcessContainer.KEY_OUTPUT_BYTES)))
		{
			// We also want to disconnect the socket in this case since we are done processing.
			_session.close();
		}
		return didSend;
	}

	@Override
	public boolean specialChanged(String special)
	{
		// This case doesn't use meta-data.
		throw Assert.unreachable();
	}
}
