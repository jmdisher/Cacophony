package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;


/**
 * These helpers write standard WebSocket state event listener events.  These all have the top-level form:
 * -"event" : (one of "create", "update", or "delete")
 * -"key" : (some user-defined key type)
 * -"value" : (some user-defined value type) - ALWAYS NULL IN "delete"
 */
public class SocketEventHelpers
{
	public static final String EVENT = "event";
	public static final String EVENT_CREATE = "create";
	public static final String EVENT_UPDATE = "update";
	public static final String EVENT_DELETE = "delete";
	public static final String EVENT_SPECIAL = "special";
	public static final String KEY = "key";
	public static final String VALUE = "value";

	public static boolean sendCreate(RemoteEndpoint endpoint, JsonValue key, JsonValue value)
	{
		return _sendCommon(endpoint, EVENT_CREATE, key, value);
	}

	public static boolean sendUpdate(RemoteEndpoint endpoint, JsonValue key, JsonValue value)
	{
		return _sendCommon(endpoint, EVENT_UPDATE, key, value);
	}

	public static boolean sendDelete(RemoteEndpoint endpoint, JsonValue key)
	{
		return _sendCommon(endpoint, EVENT_DELETE, key, Json.NULL);
	}

	public static boolean sendSpecial(RemoteEndpoint endpoint, JsonValue special)
	{
		return _sendCommon(endpoint, EVENT_SPECIAL, special, Json.NULL);
	}


	private static boolean _sendCommon(RemoteEndpoint endpoint, String event, JsonValue key, JsonValue value)
	{
		JsonObject root = new JsonObject();
		root.set(EVENT, event);
		root.set(KEY, key);
		root.set(VALUE, value);
		String serialized = root.toString();
		boolean success;
		try
		{
			endpoint.sendString(serialized);
			success = true;
		}
		catch (IOException e)
		{
			success = false;
		}
		return success;
	}
}
