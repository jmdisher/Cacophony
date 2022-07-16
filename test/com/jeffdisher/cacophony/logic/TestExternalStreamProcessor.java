package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;


public class TestExternalStreamProcessor
{
	@Test
	public void testCat() throws Throwable
	{
		ExternalStreamProcessor processor = new ExternalStreamProcessor("cat --show-ends");
		byte[] bytes = "Testing 1 2 3...\nNew line\n".getBytes();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		long[] finalProgress = new long[1];
		String[] lastError = new String[1];
		CountDownLatch doneLatch = new CountDownLatch(1);
		Consumer<Long> progressCallback = (Long sizeBytes) -> {
			finalProgress[0] = sizeBytes;
		};
		Consumer<String> errorCallback = (String error) -> {
			lastError[0] = error;
		};
		Runnable doneCallback = () -> {
			doneLatch.countDown();
		};
		processor.start(new ByteArrayInputStream(bytes), output, progressCallback, errorCallback, doneCallback);
		doneLatch.await();
		
		long size = processor.stop();
		Assert.assertNull(lastError[0]);
		Assert.assertEquals(26L, finalProgress[0]);
		Assert.assertEquals(28L, size);
		byte[] expected = "Testing 1 2 3...$\nNew line$\n".getBytes();
		Assert.assertArrayEquals(expected, output.toByteArray());
	}

	// NOTE:  This is just for testing/demonstrating the video transcoder interaction so it is ignored.
	@Ignore
	@Test
	public void testFfmpeg() throws Throwable
	{
		ExternalStreamProcessor processor = new ExternalStreamProcessor("ffmpeg -i -  -c:v libvpx-vp9 -b:v 256k -filter:v fps=15 -c:a libopus -b:a 32k -f webm  -");
		FileInputStream input = new FileInputStream(new File("/tmp/original.webm"));
		FileOutputStream output = new FileOutputStream(new File("/tmp/processed.webm"));
		long[] finalProgress = new long[1];
		String[] lastError = new String[1];
		CountDownLatch doneLatch = new CountDownLatch(1);
		Consumer<Long> progressCallback = (Long sizeBytes) -> {
			System.out.println(sizeBytes);
			finalProgress[0] = sizeBytes;
		};
		Consumer<String> errorCallback = (String error) -> {
			lastError[0] = error;
		};
		Runnable doneCallback = () -> {
			doneLatch.countDown();
		};
		processor.start(input, output, progressCallback, errorCallback, doneCallback);
		doneLatch.await();
		
		long size = processor.stop();
		System.out.println("COMPLETED:  " + size + " bytes");
	}
}
