// This is actually a static page to call the back-end REST interfaces, when running in interactive mode, but is replaced by the generated file when running in the offline mode.
// This file depends on rest.js being loaded, as it uses its REST global variable.  We can't use modules to load this dependency since that breaks in "file://" URLs, which the offline mode uses.

function API_loadPublicKey()
{
	return new Promise(resolve => {
		REST.GET("/home/publicKey")
			.then(result => result.text())
			.then(text => resolve(text));
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

function API_getAllPostsForUser(publicKey)
{
	return new Promise(resolve => {
		REST.GET("/server/postHashes/" + publicKey)
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

function API_getPost(hash)
{
	return new Promise(resolve => {
		REST.GET("/server/postStruct/" + hash)
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

