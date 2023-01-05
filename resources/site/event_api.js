// These helpers expose our "event-based update API".
// In this system, the UI is constructed by receiving events which come from the server, via a persistent WebSocket connection.
// The events are structured as such, expressed in terms of how the given callbacks will receive data (note that key and value types are use-case specific):
// -onSocketOpen(socket)
//	-not part of our event API but called with the socket when the connection opens
// -onCreate(key, value)
//	-creates a new key with the given value
// -onUpdate(key, value)
//	-updates an existing key with the given value
// -onDelete(key)
//	-deletes the given existing key
// -onSocketClose(event)
//	-not part of our event API but called with the closing event when the socket connection drops
function createWebSocketStateEventListener(url, protocol, onSocketOpen, onCreate, onUpdate, onDelete, onSocketClose)
{
	let socket = new WebSocket(url, protocol);
	socket.onopen = function()
	{
		console.log("State event listener open: " + protocol);
		onSocketOpen(socket);
	}
	socket.addEventListener('message', function(event)
	{
		// This format is defined in SocketEventHelpers.java.
		let object = JSON.parse(event.data);
		if ('create' === object.event)
		{
			onCreate(object.key, object.value);
		}
		else if ('update' === object.event)
		{
			onUpdate(object.key, object.value);
		}
		else if ('delete' === object.event)
		{
			onDelete(object.key);
		}
		else
		{
			console.log("unknown event type: " + object.event);
		}
	});
	socket.onclose = function(e)
	{
		onSocketClose(e);
	}
	return socket;
}


// To namespace these, since we can't use _actual_ module semantics (since a file with this name is exported for both http and file - file can't use modules for some bogus reason), we will attach these methods to an object for export.
// This also gives us a single point where we can associate the URL and protocol name to the definitions in InteractiveServer.java.
var EVENTS_API = {
	backgroundStatus: function(onSocketOpen, onCreate, onUpdate, onDelete, onSocketClose) { createWebSocketStateEventListener("ws://127.0.0.1:8000/backgroundStatus", "event_api"
		, onSocketOpen, onCreate, onUpdate, onDelete, onSocketClose
	); },
};
