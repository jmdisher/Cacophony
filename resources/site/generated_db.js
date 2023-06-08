// This is actually a static page to call the back-end REST interfaces, when running in interactive mode, but is replaced by the generated file when running in the offline mode.
// This file depends on rest.js being loaded, as it uses its REST global variable.  We can't use modules to load this dependency since that breaks in "file://" URLs, which the offline mode uses.

// Loads the currently-selected public key from the server, null if there is no selected key (usually because there are no users).
function API_loadPublicKey()
{
	return new Promise(resolve => {
		REST.GET("/home/publicKey")
			.then(function(result)
			{
				if (result.ok)
				{
					result.text().then(text => resolve(text));
				}
				else
				{
					// Annoyingly, it seems like this error is still logged, even though we are handling it.
					resolve(null);
				}
			})
		;
	});
}

function API_getInfoForUser(publicKey)
{
	return new Promise(resolve => {
		REST.GET("/server/userInfo/" + publicKey)
			.then(result => result.json())
			.then(json => resolve(json));
	});
}

function API_getRecommendedUsers(publicKey)
{
	return new Promise(resolve => {
		REST.GET("/server/recommendedKeys/" + publicKey)
			.then(result => result.json())
			.then(json => resolve(json));
	});
}

// Returns an object describing the post with the given hash, via a promise.  Implementation of this is in GET_PostStruct.java and defines these keys:
// -cached (boolean)
// -name (string)
// -description (string)
// -publishedSecondsUtc (long)
// -discussionUrl (string)
// -publisherKey (string)
// -thumbnailUrl (string)
// -videoUrl (string)
// -audioUrl (string)
function API_getPost(hash, forceCache)
{
	return new Promise(resolve => {
		REST.GET("/server/postStruct/" + hash + "/" + (forceCache ? "FORCE" : "OPTIONAL"))
			.then(result => result.json())
			.then(json => resolve(json));
	});
}

function API_getFollowedKeys()
{
	return new Promise(resolve => {
		REST.GET("/followees/keys")
			.then(result => result.json())
			.then(json => resolve(json));
	});
}

function API_getPrefs()
{
	return new Promise(resolve => {
		REST.GET("/server/prefs")
			.then(result => result.json())
			.then(json => resolve(json));
	});
}

function API_getVersion()
{
	return new Promise(resolve => {
		REST.GET("/server/version")
			.then(result => result.json())
			.then(json => resolve(json));
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

