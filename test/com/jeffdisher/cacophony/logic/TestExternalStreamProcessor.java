package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
		Consumer<Long> doneCallback = (Long outputSizeBytes) -> {
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

	// NOTE:  This is just for testing/demonstrating the video transcoder interaction so it is ignored as it depends on external resources and the specific program.
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
		Consumer<Long> doneCallback = (Long outputSizeBytes) -> {
			doneLatch.countDown();
		};
		processor.start(input, output, progressCallback, errorCallback, doneCallback);
		doneLatch.await();
		
		long size = processor.stop();
		System.out.println("COMPLETED:  " + size + " bytes");
	}

	@Test
	public void testMissingProgram() throws Throwable
	{
		ExternalStreamProcessor processor = new ExternalStreamProcessor("foobar --show-ends");
		byte[] bytes = "Testing 1 2 3...\nNew line\n".getBytes();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		boolean[] shouldFail = new boolean[] {false};
		Consumer<Long> progressCallback = (Long sizeBytes) -> {
			// Not expected.
			shouldFail[0] = true;
		};
		Consumer<String> errorCallback = (String error) -> {
			// Not expected.
			shouldFail[0] = true;
		};
		Consumer<Long> doneCallback = (Long outputSizeBytes) -> {
			// Not expected.
			shouldFail[0] = true;
		};
		try
		{
			processor.start(new ByteArrayInputStream(bytes), output, progressCallback, errorCallback, doneCallback);
			Assert.fail();
		}
		catch (IOException e)
		{
			// We were expecting this since this is how the failure manifests when the program can't be started
		}
		Assert.assertFalse(shouldFail[0]);
	}

	@Test
	public void testFailingProgram() throws Throwable
	{
		ExternalStreamProcessor processor = new ExternalStreamProcessor("cat random bogus args");
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
		Consumer<Long> doneCallback = (Long outputSizeBytes) -> {
			doneLatch.countDown();
		};
		processor.start(new ByteArrayInputStream(bytes), output, progressCallback, errorCallback, doneCallback);
		doneLatch.await();
		
		long size = processor.stop();
		// We expect to see an error since the program exited unexpectedly.
		// WARNING:  The observed reason for the failure is racy - either the stream closing or the process exiting.
		Assert.assertNotNull(lastError[0]);
		Assert.assertEquals(26L, finalProgress[0]);
		// Size returned will be -1 since there was a failure.
		Assert.assertEquals(-1, size);
		// There should be no output
		Assert.assertEquals(0, output.toByteArray().length);
	}
}
