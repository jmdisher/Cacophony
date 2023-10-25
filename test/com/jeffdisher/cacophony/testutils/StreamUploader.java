package com.jeffdisher.cacophony.testutils;

import java.io.ByteArrayInputStream;

import com.jeffdisher.cacophony.net.Uploader;
import com.jeffdisher.cacophony.types.IpfsFile;

import io.ipfs.multiaddr.MultiAddress;


/**
 * Accepts only one argument:  The IPFS gateway API (/ip4/127.0.0.1/tcp/5001).
 * Accepts a file from stdin, posting it to the gateway, and then outputs the final hash of the stored file.
 */
public class StreamUploader
{
	public static void main(String[] args) throws Exception
	{
		if (1 == args.length)
		{
			MultiAddress addr = new MultiAddress(args[0]);
			String host = addr.getHost();
			int port = addr.getPort();
			Uploader uploader = new Uploader();
			uploader.start();
			IpfsFile output = uploader.uploadFileInline(host, port, new ByteArrayInputStream("FOO".getBytes()));
			uploader.stop();
			System.out.println(output.toSafeString());
		}
		else
		{
			System.err.println("Usage:  StreamUploader <ipfs gateway API>");
			System.exit(1);
		}
	}
}
