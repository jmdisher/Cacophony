package com.jeffdisher.cacophony.logic;

import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.InputStreamRequestContent;
import org.eclipse.jetty.client.util.MultiPartRequestContent;
import org.eclipse.jetty.http.HttpMethod;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * We use the Jetty HTTP client to upload files to IPFS instead of the IPFS Java library since it loads the entire file
 * into memory before posting, thus driving up our heap requirements for no good reason.
 * This is because the "/api/v0/add" uses a multi-part POST and the mechanism the IPFS library uses sends this with the
 * more common "Content-Length" header, which requires knowing the total size, which it only knows if it reads the
 * entire stream into memory.
 * Jetty will instead use "Transfer-Encoding: chunked" if it is given a stream, thus meaning it doesn't need to read
 * the entire stream.
 * This difference can be observed with Curl (connecting to NetCat "nc -l 5001"):
 * -echo "F" | curl -H -X POST -F file=@- http://127.0.0.1:5001/api/v0/add
 * -echo "F" | curl -H "Transfer-Encoding: chunked" -X POST -F file=@- http://127.0.0.1:5001/api/v0/add
 */
public class Uploader
{
	private final String ADD_API_PATH = "/api/v0/add";

	private final HttpClient _client;

	public Uploader()
	{
		_client = new HttpClient();
	}

	public void start() throws Exception
	{
		_client.start();
	}

	public void stop() throws Exception
	{
		_client.stop();
	}

	public IpfsFile uploadFileInline(String host, int port, InputStream stream) throws InterruptedException, TimeoutException, ExecutionException
	{
		MultiPartRequestContent multiPart = new MultiPartRequestContent();
		multiPart.addFilePart("file", "file", new InputStreamRequestContent(stream), null);
		multiPart.close();
		ContentResponse response = _client.newRequest(host, port)
				.method(HttpMethod.POST)
				.path(ADD_API_PATH)
				.body(multiPart)
				.send();
		String string = response.getContentAsString();
		JsonObject object = Json.parse(string).asObject();
		// This object has keys:
		// -"Name" - the name "file" we sent in the upload.
		// -"Hash" - the IPFS CID we actually want.
		// -"Size" - this is the "CumulativeSize", including IPFS overhead, not just the bytes uploaded.
		String rawHash = object.get("Hash").asString();
		return IpfsFile.fromIpfsCid(rawHash);
	}
}
