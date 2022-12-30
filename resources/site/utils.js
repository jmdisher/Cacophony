// General helpers.

function readGetVar(name)
{
	var vars = {};
	// We use a regular expression to get the GET args as an associative array.
	// To explain this:
	// -skip over all ? or & which come before any other content
	// -read the key by getting everything which isn't =
	// -skip the =
	// -read the value by getting everything which isn't &
	var parts = window.location.href.replace(/[?&]+([^=]+)=([^&]*)/gi, function(ignored, key, value) {
		vars[decodeURIComponent(key)] = decodeURIComponent(value);
	});
	return vars[name];
}

// This is used for WebSocket state event listener events, corresponding to the shape seen in SocketEventHelpers.java.  It may move elsewhere in the future, as its usage expands.
function createWebSocketStateEventListener(url, protocol, onCreate, onUpdate, onDelete, onSocketClose)
{
	let socket = new WebSocket(url, protocol);
	socket.onopen = function()
	{
		console.log("State event listener open: " + protocol);
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

