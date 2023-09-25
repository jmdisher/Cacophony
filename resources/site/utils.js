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
		// We will get a null if we failed to load this post.
		let object = null;
		if (null !== elt)
		{
			// We want to make sure the description isn't too long to reasonably render (since it is allowed to be unbounded in length, at the protocol level).
			let description = UTILS_truncateDescription(elt["description"]);
			// We can't update the placeholder since the $apply() won't see nested updates so replace this.
			object = {
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
		}
		else
		{
			object = {
				"hasDataToCache": placeholder["hasDataToCache"],
				"elementHash": placeholder["elementHash"],
				"readableDate": "Unknown date",
				"name": "Failed to load",
				"description": "Failed to load post information",
				"publisherKey": placeholder["publisherKey"],
				"isDeleting": placeholder["isDeleting"],
				"thumbnailUrl": placeholder["thumbnailUrl"],
				"replyTo": placeholder["replyTo"],
			}
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

// Returns a Promise which resolves an array of structs with {width, height, frameRate}.
function UTILS_checkCamera(includeAudio)
{
	// A note about this utility:  I originally tried to make this automatically discover all "sensible" resolutions by stepping past each accepted constraint until they were all enumerated.
	// This seemed to work in FireFox but Chromium responded that it can manage every requested resolution in the range, sometimes with completely bogus framerate, so this isn't feasible.
	// In short, this means that FireFox gave me something like what I wanted while Chromium gave me what I asked for.
	// As it isn't useful to list 1000s of options (things like 640x481), we will instead just try this basic hard-coded list of "reasonable" resolutions and framerates.
	// These are just a collection of likely 16:9 and 4:3 ratios.
	const kResolutions = [ [320, 240]
	, [640, 480]
	, [1024, 768]
	// 720p
	, [1280, 720]
	// 1080p
	, [1920, 1080]
	// 4K UHD-1
	, [3840, 2160]
	];
	const kFrameRates = [10, 15, 30, 60];
	
	return new Promise((resolve, reject) => {
		let resultArray = [];
		let resolutionIndex = 0;
		let frameRateIndex = 0;
		
		// We start by just asking for the camera, saying it needs video (and potentially audio).
		let startConstraints = {video: true};
		if (includeAudio)
		{
			startConstraints.audio = true;
		}
		navigator.mediaDevices
			.getUserMedia(startConstraints)
			.then((stream) => {
				// We have a camera so start asking about constraints.
				// We seem to only need the first track (haven't seen examples where there is more than one, on my test system).
				let tracks = stream.getVideoTracks();
				let track = tracks[0];
				check(stream, track);
			})
			.catch(reject);
		
		// Essentially the "asynchronous 2-level for loop".  Increments the indices and returns result when completed.
		function nextScan(stream, track)
		{
			// We treat the resolutions as columns and the frame rates as rows, scanning row-first.
			frameRateIndex += 1;
			if (frameRateIndex < kFrameRates.length)
			{
				check(stream, track);
			}
			else
			{
				frameRateIndex = 0;
				resolutionIndex += 1;
				if (resolutionIndex < kResolutions.length)
				{
					check(stream, track);
				}
				else
				{
					// We are done.
					stream.getTracks().forEach(function(track) { track.stop(); });
					resolve(resultArray);
				}
			}
		}
		// Performs the next check described by the indices.  Appends to the resultArray on success.
		function check(stream, track)
		{
			let videoConstraints = {
				width: {exact: kResolutions[resolutionIndex][0]},
				height: {exact: kResolutions[resolutionIndex][1]},
				frameRate: {exact: kFrameRates[frameRateIndex]},
			};
			track.applyConstraints(videoConstraints)
				.then(() => {
					// This is valid, so write what we have.
					resultArray.push({
						width: kResolutions[resolutionIndex][0],
						height: kResolutions[resolutionIndex][1],
						frameRate: kFrameRates[frameRateIndex],
					});
					
					// Now, move on to the next.
					nextScan(stream, track);
				})
				.catch((err) => {
					// If this failed, just check the next.
					nextScan(stream, track);
				});
		}
	});
}

// We replace newlines with spaces and restrict the length of the description.
function UTILS_truncateDescription(description)
{
	// Replace newlines.
	const regex = new RegExp("\n", "g");
	description = description.replace(regex, " ");
	
	// Restrict total string length.
	const kMaxLength = 250;
	if (description.length > kMaxLength)
	{
		// We use mismatched truncation to avoid spilling just a few chars - the actual limits are unimportant.
		description = description.slice(0, (kMaxLength - 3)) + "...";
	}
	return description;
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

