// NOTE:  This file is intended to be replaced with a generated data store.  This data is provided only for static page layout testing as it is in the same shape as the generated code.
// In order to use this to test UI changes, rename/copy it to "generated_db.js" (this "fake" name is used to differentiate it and because we can't override static files in Breakwater).
// Note that most of the data here is fake, just to have the right shape, so don't infer too much meaning from it.

var DATA_common = {
	"publicKey": "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F",
};
var DATA_version = {
	"hash": "HASH",
	"version": "VERSION",
};
var DATA_prefs = {
	"edgeSize": 720,
	"followerCacheBytes": 2500000000,
};
var DATA_userInfo = {
	"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F": {
		"name": "Local user",
		"description": "Description for local user",
		"userPicUrl": "../unknown_user.png",
		"email": "email@example.com",
		"website": "http://example.com",
	},
	"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141": {
		"name": "User 2",
		"description": "Description for user 2",
		"userPicUrl": "../unknown_user.png",
		"email": null,
		"website": null,
	},
};
var DATA_elements = {
	"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1J1": {
		"cached": false,
		"name": "entry 1",
		"description": "just a test",
		"publishedSecondsUtc": 1000,
		"discussionUrl" : null,
	},
	"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1JF": {
		"cached": true,
		"name": "entry 2",
		"description": "just another test",
		"publishedSecondsUtc": 2000,
		"thumbnailUrl": "http://localhost:8080/ipfs/QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1Ju",
		"videoUrl": "http://localhost:8080/ipfs/QmScitMGZ1FU7EzCkp8nTzadFTtneLpJLP5pBqEq3Uh536",
		"discussionUrl" : "http://example.com/1",
	},
	"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1J6": {
		"cached": true,
		"name": "other user",
		"description": "just a test",
		"publishedSecondsUtc": 10000,
		"thumbnailUrl": "http://localhost:8080/ipfs/QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1Ju",
		"videoUrl": "http://localhost:8080/ipfs/QmScitMGZ1FU7EzCkp8nTzadFTtneLpJLP5pBqEq3Uh536",
		"discussionUrl" : "http://example.com/2",
	},
	"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1JD": {
		"cached": true,
		"name": "other user no thumbnail",
		"description": "no thumbnail",
		"publishedSecondsUtc": 10001,
		"thumbnailUrl": null,
		"videoUrl": "http://localhost:8080/ipfs/QmScitMGZ1FU7EzCkp8nTzadFTtneLpJLP5pBqEq3Uh536",
		"discussionUrl" : null,
	},
	"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1JJ": {
		"cached": true,
		"name": "other user no video",
		"description": "no video",
		"publishedSecondsUtc": 10002,
		"thumbnailUrl": "http://localhost:8080/ipfs/QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1Ju",
		"videoUrl": null,
		"discussionUrl" : "http://example.com/3",
	},
	"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1JA": {
		"cached": true,
		"name": "other user no data",
		"description": "no thumbnail or video",
		"publishedSecondsUtc": 10003,
		"thumbnailUrl": null,
		"videoUrl": null,
		"discussionUrl" : null,
	},
	"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZZZZ": {
		"cached": true,
		"name": "Simulated blog post",
		"description": "This is just to test out something which looks like a blog post.\nIn theory, we should be able to see this split across lines.\n\nIt will require that we process this, though.",
		"publishedSecondsUtc": 1651104928,
		"thumbnailUrl": null,
		"videoUrl": null,
		"discussionUrl" : "http://example.com/4",
	},
};
var DATA_userPosts = {
	"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F": [
		"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1J1",
		"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1JF",
		"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZZZZ",
	],
	"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141": [
		"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1J6",
		"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1JD",
		"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1JJ",
		"QmfJTCrjmcJjscXsyRxUX1woatdSucvJp7CJgfDUGZZ1JA",
	],
};
var DATA_recommended = {
	"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F": [
		"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141",
		"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo146",
	],
	"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141": [
		"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F",
		"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo146",
	],
};
var DATA_following = [
	"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141",
];


// Note that we will temporarily just pass this data back, asynchronously, to get the rest of the code into the async shape required for the interactive mode to eventually fetch this from REST, not the generated file.
function API_loadPublicKey()
{
	return new Promise(resolve => {
		setTimeout(() => {
			resolve(DATA_common["publicKey"]);
		});
	});
}

function API_getInfoForUser(publicKey)
{
	return new Promise(resolve => {
		setTimeout(() => {
			resolve(DATA_userInfo[publicKey]);
		});
	});
}

function API_getAllPostsForUser(publicKey)
{
	return new Promise(resolve => {
		setTimeout(() => {
			resolve(DATA_userPosts[publicKey]);
		});
	});
}

function API_getRecommendedUsers(publicKey)
{
	return new Promise(resolve => {
		setTimeout(() => {
			resolve(DATA_recommended[publicKey]);
		});
	});
}

function API_getPost(hash)
{
	return new Promise(resolve => {
		setTimeout(() => {
			resolve(DATA_elements[hash]);
		});
	});
}

function API_getFollowedKeys()
{
	return new Promise(resolve => {
		setTimeout(() => {
			resolve(DATA_following);
		});
	});
}

function API_getPrefs()
{
	return new Promise(resolve => {
		setTimeout(() => {
			resolve(DATA_prefs);
		});
	});
}

function API_getVersion()
{
	return new Promise(resolve => {
		setTimeout(() => {
			resolve(DATA_version);
		});
	});
}

