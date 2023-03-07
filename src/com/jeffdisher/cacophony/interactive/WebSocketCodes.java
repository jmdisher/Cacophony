package com.jeffdisher.cacophony.interactive;


/**
 * Error/status codes we define for our WebSocket-based communication.  These are reported when closing the socket.
 * Note that a code 1000 is considered "NORMAL" close.
 * WebSocket close codes in the range 4000-4999 are "private" and are for use in non-registered applications.
 */
public class WebSocketCodes
{
	/**
	 * Used by the "background status" socket since it actually accepts "commands" coming back down the socket, from the
	 * client.
	 */
	public final static int INVALID_COMMAND = 4000;
	/**
	 * Used in general cases where the IPFS connection failed or timed out.
	 */
	public final static int IPFS_CONNECTION_ERROR = 4001;
	/**
	 * Used when the server is accessed with the wrong XSRF or from the wrong IP.
	 */
	public final static int SECURITY_FAILED = 4002;
	/**
	 * Used in cases where a resource or operation is requested but it isn't found.
	 */
	public final static int NOT_FOUND = 4003;
	/**
	 * Used in cases where the socket requests an action to start but it is already running.
	 */
	public final static int ALREADY_STARTED = 4004;
	/**
	 * Used in cases where the socket requests an action to start but it failed to start.
	 */
	public final static int FAILED_TO_START = 4005;
}
