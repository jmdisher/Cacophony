package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;


/**
 * These helpers write standard WebSocket state event listener events.  These all have the top-level form:
 * -"event" : (one of "create", "update", "delete", or "special")
 * -"key" : (some user-defined key type)
 * -"value" : (some user-defined value type) - ALWAYS NULL IN "delete" or "special"
 * -"isNewest" : true or false - true meaning that the value is the newest to be observed, false meaning it is the
 * oldest or doesn't matter (this is only relevant for "create" events).
 * While create-update-delete represent a simple key-value projection, "special" is intended to be for out-of-band state
 * setting.  This is a single value used to describe something different ("special") in these otherwise simple cases.
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
	public static final String IS_NEWEST = "isNewest";

	/**
	 * Sends a "create" message.
	 * 
	 * @param endpoint The socket.
	 * @param key The key (user-defined).
	 * @param value The value (user-defined).
	 * @param isNewest True if this should be considered the newest key, false for the oldest.
	 * @return True if the message was sent, or false if there was a network error.
	 */
	public static boolean sendCreate(RemoteEndpoint endpoint, JsonValue key, JsonValue value, boolean isNewest)
	{
		return _sendCommon(endpoint, EVENT_CREATE, key, value, isNewest);
	}

	/**
	 * Sends an "update" message.
	 * 
	 * @param endpoint The socket.
	 * @param key The key (user-defined).
	 * @param value The value (user-defined).
	 * @return True if the message was sent, or false if there was a network error.
	 */
	public static boolean sendUpdate(RemoteEndpoint endpoint, JsonValue key, JsonValue value)
	{
		return _sendCommon(endpoint, EVENT_UPDATE, key, value, false);
	}

	/**
	 * Sends a "delete" message.
	 * 
	 * @param endpoint The socket.
	 * @param key The key (user-defined).
	 * @return True if the message was sent, or false if there was a network error.
	 */
	public static boolean sendDelete(RemoteEndpoint endpoint, JsonValue key)
	{
		return _sendCommon(endpoint, EVENT_DELETE, key, Json.NULL, false);
	}

	/**
	 * Sends a "special" message.
	 * 
	 * @param endpoint The socket.
	 * @param special The special value (user-defined).
	 * @return True if the message was sent, or false if there was a network error.
	 */
	public static boolean sendSpecial(RemoteEndpoint endpoint, JsonValue special)
	{
		return _sendCommon(endpoint, EVENT_SPECIAL, special, Json.NULL, false);
	}


	private static boolean _sendCommon(RemoteEndpoint endpoint, String event, JsonValue key, JsonValue value, boolean isBackward)
	{
		JsonObject root = new JsonObject();
		root.set(EVENT, event);
		root.set(KEY, key);
		root.set(VALUE, value);
		root.set(IS_NEWEST, isBackward);
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
