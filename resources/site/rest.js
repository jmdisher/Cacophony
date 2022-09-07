// This module just defines some basic REST-related wrapper functions over the fetch API.

function GET(path)
{
	return fetch("http://127.0.0.1:8000" + path, {
		method: "GET",
	});
}

function POST(path)
{
	return fetch("http://127.0.0.1:8000" + path, {
		method: "POST",
	});
}

function POST_withBinary(path, data)
{
	return fetch("http://127.0.0.1:8000" + path, {
		method: "POST",
		headers: { "Content-Type": "application/octet-stream" },
		body: data,
	});
}

function POST_asForm(path, variables)
{
	let formParts = [];
	for (let property in variables) {
		let encodedKey = encodeURIComponent(property);
		let encodedValue = encodeURIComponent(variables[property]);
		formParts.push(encodedKey + "=" + encodedValue);
	}
	let formBody = formParts.join("&");
	return fetch("http://127.0.0.1:8000" + path, {
		method: "POST",
		headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
		body: formBody,
	});
}

function DELETE(path)
{
	return fetch("http://127.0.0.1:8000" + path, {
		method: "DELETE",
	});
}

// To namespace these, since we can't use _actual_ module semantics (since we export this for http and file - file can't use modules for some bogus reason), we will attach these methods to an object for export.
var REST = {
	"GET": GET,
	"POST": POST,
	"POST_withBinary": POST_withBinary,
	"POST_asForm": POST_asForm,
	"DELETE": DELETE,
};

