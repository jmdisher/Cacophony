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


