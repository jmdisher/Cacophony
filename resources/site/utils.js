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

// Adds a placeholder for the "hash" StreamRecord into "array", adding to beginning if "isNewest" or the end.
// Asynchronously starts loading the actual information for the StreamRecord, calling "updateCallback" when returned.
// Keys in the element added to the array:
// "cached"
// "elementHash"
// "name"
// "description"
// "readableDate"
// "isDeleting"
// "thumbnailUrl"
// "publisherKey"
function addElementHashToArray(array, hash, isNewest, updateCallback)
{
	// Add empty objects to the array since we will replace them asynchronously.
	let placeholder = {
		"cached": false,
		"elementHash": hash,
		"name": "Loading...",
		"description": "Loading...",
		"readableDate": "Loading...",
		"isDeleting": false,
		"thumbnailUrl": null,
		"publisherKey": null,
	};
	// We want to build this backward so that the most recent additions are at the top of the screen.
	if (isNewest)
	{
		array.unshift(placeholder);
	}
	else
	{
		array.push(placeholder);
	}
	API_getPost(hash, false).then(elt => {
		// We want to make sure the description isn't too long to reasonably render (since it is allowed to be unbounded in length, at the protocol level).
		let description = elt["description"];
		if (description.length > 135)
		{
			// We use mismatched truncation to avoid spilling just a few chars - the actual limits are unimportant.
			description = description.slice(0, 130) + "...";
		}
		// We can't update the placeholder since the $apply() won't see nested updates so replace this.
		let object = {
			"cached": elt["cached"],
			"elementHash": hash,
			"readableDate": new Date(elt["publishedSecondsUtc"] * 1000).toLocaleString(),
			"name": elt["name"],
			"description": description,
			"publisherKey": elt["publisherKey"],
			"isDeleting": false,
		}
		if (elt["cached"])
		{
			object["thumbnailUrl"] = elt["thumbnailUrl"];
		}
		// We need to replace the index due to the earlier mention of $apply.
		// (we will use indexOf() since this is an exact instance match)
		let index = array.indexOf(placeholder);
		array[index] = object;
		updateCallback();
	});
}

function renderLongTextIntoElement(element, longText)
{
	// We write the data in as text (meaning it won't change the meaning of the DOM), then we replace the new lines with explicit break tags in the HTML.
	// We do it in this order for security reasons:  Any other tags in the text will be rendered as text instead of modifying the DOM, so only the <br /> we add will modify it.
	element.innerText = longText;
	element.innerHTML = element.innerHTML.replace(/\/n/g, "<br />");
}
