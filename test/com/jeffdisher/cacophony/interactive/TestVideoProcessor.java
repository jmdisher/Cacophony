package com.jeffdisher.cacophony.interactive;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.local.v3.Draft;
import com.jeffdisher.cacophony.data.local.v3.SizedElement;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IDraftWrapper;


public class TestVideoProcessor
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	@Test
	public void testBasicProcess() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftManager draftManager = new DraftManager(directory);
		int draftId = 1;
		byte[] bytes = _createOriginalDraft(draftManager, draftId);
		
		long[] out_processed = new long[1];
		String[] out_error = new String[1];
		long[] out_outputSize = new long[1];
		CountDownLatch latch = new CountDownLatch(1);
		VideoProcessor processor = new VideoProcessor(new VideoProcessor.ProcessWriter()
		{
			@Override
			public void totalBytesProcessed(long bytesProcessed)
			{
				Assert.assertTrue(bytesProcessed > out_processed[0]);
				out_processed[0] = bytesProcessed;
			}
			@Override
			public void processingError(String error)
			{
				out_error[0] = error;
			}
			@Override
			public void processingDone(long outputSizeBytes)
			{
				out_outputSize[0] = outputSizeBytes;
				latch.countDown();
			}
		}, draftManager, draftId, "cat --show-ends");
		
		// Wait for this to finish.
		latch.await();
		processor.stopProcess();
		
		byte[] expected = "Testing 1 2 3...$\nNew line$\n".getBytes();
		byte[] readBack = null;
		try (InputStream stream = draftManager.openExistingDraft(draftId).readProcessedVideo())
		{
			readBack = stream.readAllBytes();
		}
		Assert.assertNull(out_error[0]);
		Assert.assertEquals(bytes.length, out_processed[0]);
		Assert.assertEquals(expected.length, out_outputSize[0]);
		Assert.assertArrayEquals(expected, readBack);
	}

	@Test
	public void testForceStopProcess() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftManager draftManager = new DraftManager(directory);
		int draftId = 1;
		_createOriginalDraft(draftManager, draftId);
		
		long[] out_processed = new long[1];
		String[] out_error = new String[1];
		long[] out_outputSize = new long[1];
		CountDownLatch latch = new CountDownLatch(1);
		// We will just cat /dev/random since it produces data slowly, but will do so forever.  This makes it easy to interrupt.
		VideoProcessor processor = new VideoProcessor(new VideoProcessor.ProcessWriter()
		{
			@Override
			public void totalBytesProcessed(long bytesProcessed)
			{
				Assert.assertTrue(bytesProcessed > out_processed[0]);
				out_processed[0] = bytesProcessed;
			}
			@Override
			public void processingError(String error)
			{
				out_error[0] = error;
			}
			@Override
			public void processingDone(long outputSizeBytes)
			{
				out_outputSize[0] = outputSizeBytes;
				latch.countDown();
			}
		}, draftManager, draftId, "cat /dev/random");
		
		// Force the background task to stop.
		processor.stopProcess();
		
		// Wait for this to finish.
		latch.await();
		
		// We just verify that the error and final size are both consistent with a failure or forced stop.
		Assert.assertEquals("Process exit status: 137", out_error[0]);
		Assert.assertEquals(-1L, out_outputSize[0]);
	}


	private byte[] _createOriginalDraft(DraftManager draftManager, int draftId) throws IOException, FileNotFoundException
	{
		draftManager.createNewDraft(draftId);
		
		// Populate the input data in the draft (and make sure that the meta-data is updated).
		IDraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		byte[] bytes = "Testing 1 2 3...\nNew line\n".getBytes();
		try (OutputStream out = wrapper.writeOriginalVideo())
		{
			out.write(bytes);
		}
		SizedElement originalVideo = new SizedElement("video/webm", 720, 1280, bytes.length);
		Draft originalDraft = wrapper.loadDraft();
		wrapper.saveDraft(new Draft(originalDraft.id()
				, originalDraft.publishedSecondsUtc()
				, originalDraft.title()
				, originalDraft.description()
				, originalDraft.discussionUrl()
				, originalDraft.thumbnail()
				, originalVideo
				, originalDraft.processedVideo()
				, originalDraft.audio()
				, originalDraft.replyTo()
		));
		return bytes;
	}
}
