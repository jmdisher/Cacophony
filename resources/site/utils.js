// General helpers.

function UTILS_readGetVar(name)
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
// "hasDataToCache"
// "elementHash"
// "name"
// "description"
// "readableDate"
// "isDeleting"
// "thumbnailUrl"
// "publisherKey"
function UTILS_addElementHashToArray(array, hash, isNewest, updateCallback)
{
	// Add empty objects to the array since we will replace them asynchronously.
	let placeholder = {
		"hasDataToCache": true,
		"elementHash": hash,
		"name": "Loading...",
		"description": "Loading...",
		"readableDate": "Loading...",
		"isDeleting": false,
		"thumbnailUrl": null,
		"publisherKey": null,
		"replyTo": null,
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
	GLOBAL_PostLoader.loadTuple(hash).then(elt => {
		// We want to make sure the description isn't too long to reasonably render (since it is allowed to be unbounded in length, at the protocol level).
		let description = elt["description"];
		if (description.length > 135)
		{
			// We use mismatched truncation to avoid spilling just a few chars - the actual limits are unimportant.
			description = description.slice(0, 130) + "...";
		}
		// We can't update the placeholder since the $apply() won't see nested updates so replace this.
		let object = {
			"hasDataToCache": elt["hasDataToCache"],
			"elementHash": elt["elementHash"],
			"readableDate": elt["readableDate"],
			"name": elt["name"],
			"description": description,
			"publisherKey": elt["publisherKey"],
			"isDeleting": false,
			"thumbnailUrl": elt["thumbnailUrl"],
			"replyTo": elt["replyTo"],
		}
		// We need to replace the index due to the earlier mention of $apply.
		// (we will use indexOf() since this is an exact instance match)
		let index = array.indexOf(placeholder);
		array[index] = object;
		updateCallback();
	});
}

function UTILS_renderLongTextIntoElement(element, longText)
{
	// We write the data in as text (meaning it won't change the meaning of the DOM), then we replace the new lines with explicit break tags in the HTML.
	// We do it in this order for security reasons:  Any other tags in the text will be rendered as text instead of modifying the DOM, so only the <br /> we add will modify it.
	element.innerText = longText;
	const regex = new RegExp("\n", "g");
	element.innerHTML = element.innerHTML.replace(regex, "<br />");
}

// Returns an object describing the post with the given hash, via a promise (null on error).  Implementation of this is in GET_PostStruct.java and defines these keys:
// -name (string)
// -description (string)
// -publishedSecondsUtc (long)
// -discussionUrl (string)
// -publisherKey (string)
// -replyTo (string) - usually null
// -hasDataToCache (boolean)
// -thumbnailUrl (string) - can be null (null if hasDataToCache)
// -videoUrl (string) - can be null (null if hasDataToCache)
// -audioUrl (string) - can be null (null if hasDataToCache)
function API_getPost(hash, forceCache)
{
	return new Promise(resolve => {
		REST.GET("/server/postStruct/" + hash + "/" + (forceCache ? "FORCE" : "OPTIONAL"))
			.then(result => result.json())
			.then(json => resolve(json))
			.catch((errorCode) => resolve(null))
		;
	});
}

// NOTE:  We need to call this "cookie" page to set the cookie to defeat some XSRF cases (we mostly rely on SameSite to make this safe).
function API_getXsrf()
{
	return new Promise(resolve => {
		REST.POST("/server/cookie")
			.then(resolve);
	});
}

