package com.jeffdisher.cacophony.interactive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.logic.RealConfigFileSystem;


public class TestInteractiveHelpers
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	@Test
	public void testEmptyList() throws Throwable
	{
		IConfigFileSystem files = _getTestingDraftFiles();
		DraftManager draftManager = new DraftManager(files.getDraftsTopLevelDirectory());
		List<Draft> drafts = InteractiveHelpers.listDrafts(draftManager);
		Assert.assertEquals(0, drafts.size());
	}

	@Test
	public void testCreateTwoAndList() throws Throwable
	{
		IConfigFileSystem files = _getTestingDraftFiles();
		DraftManager draftManager = new DraftManager(files.getDraftsTopLevelDirectory());
		InteractiveHelpers.createNewDraft(draftManager, 1);
		InteractiveHelpers.createNewDraft(draftManager, 2);
		List<Draft> drafts = InteractiveHelpers.listDrafts(draftManager);
		Assert.assertEquals(2, drafts.size());
	}

	@Test
	public void testSetTitle() throws Throwable
	{
		IConfigFileSystem files = _getTestingDraftFiles();
		DraftManager draftManager = new DraftManager(files.getDraftsTopLevelDirectory());
		int id = 1;
		String title = "new title";
		String description = "long description";
		InteractiveHelpers.createNewDraft(draftManager, id);
		InteractiveHelpers.updateDraftText(draftManager, id, title, description, null);
		List<Draft> drafts = InteractiveHelpers.listDrafts(draftManager);
		Assert.assertEquals(1, drafts.size());
		Draft draft = drafts.get(0);
		Assert.assertEquals(id, draft.id());
		Assert.assertEquals(title, draft.title());
		Assert.assertEquals(description, draft.description());
	}

	@Test
	public void testDefaultAndDelete() throws Throwable
	{
		IConfigFileSystem files = _getTestingDraftFiles();
		DraftManager draftManager = new DraftManager(files.getDraftsTopLevelDirectory());
		int id = 1;
		InteractiveHelpers.createNewDraft(draftManager, id);
		Draft draft = InteractiveHelpers.readExistingDraft(draftManager, id);
		Assert.assertEquals(id, draft.id());
		Assert.assertEquals(0L, draft.publishedSecondsUtc());
		Assert.assertEquals("New Draft - " + id, draft.title());
		Assert.assertEquals("No description", draft.description());
		Assert.assertNull(draft.thumbnail());
		Assert.assertNull(draft.originalVideo());
		Assert.assertNull(draft.processedVideo());
		
		InteractiveHelpers.deleteExistingDraft(draftManager, id);
		List<Draft> drafts = InteractiveHelpers.listDrafts(draftManager);
		Assert.assertEquals(0, drafts.size());
	}

	@Test
	public void testThumbnail() throws Throwable
	{
		IConfigFileSystem files = _getTestingDraftFiles();
		DraftManager draftManager = new DraftManager(files.getDraftsTopLevelDirectory());
		int id = 1;
		int height = 720;
		int width = 1280;
		String string = "test data";
		InteractiveHelpers.createNewDraft(draftManager, id);
		ByteArrayInputStream input = new ByteArrayInputStream(string.getBytes());
		InteractiveHelpers.saveThumbnailFromStream(draftManager, id, height, width, input);
		
		String[] outString = new String[1];
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		InteractiveHelpers.loadThumbnailToStream(draftManager, id, (String mime) -> outString[0] = mime, outStream);
		Assert.assertEquals("image/jpeg", outString[0]);
		Assert.assertArrayEquals(string.getBytes(), outStream.toByteArray());
		
		// Make sure that we can delete this without issue.
		Assert.assertTrue(InteractiveHelpers.deleteThumbnail(draftManager, id));
		String[] outString2 = new String[1];
		outStream = new ByteArrayOutputStream();
		try
		{
			InteractiveHelpers.loadThumbnailToStream(draftManager, id, (String mime) -> outString2[0] = mime, outStream);
			Assert.fail();
		}
		catch (FileNotFoundException e)
		{
			// Expected.
		}
		Assert.assertNull(outString2[0]);
		Assert.assertEquals(0, outStream.size());
	}

	@Test
	public void testSaveVideo() throws Throwable
	{
		IConfigFileSystem files = _getTestingDraftFiles();
		DraftManager draftManager = new DraftManager(files.getDraftsTopLevelDirectory());
		int id = 1;
		InteractiveHelpers.createNewDraft(draftManager, id);
		
		// Save the video content.
		byte[] data = "Testing video".getBytes();
		VideoSaver saver = InteractiveHelpers.openNewVideo(draftManager, id);
		InteractiveHelpers.appendToNewVideo(saver, data, 0, data.length);
		InteractiveHelpers.closeNewVideo(saver, "video/webm", 5, 6);
		
		// Re-read it.
		String[] outMime = new String[1];
		long[] outByteSize = new long[1];
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		InteractiveHelpers.writeOriginalVideoToStream(draftManager, id, (String mime, Long byteSize) -> {
			outMime[0] = mime;
			outByteSize[0] = byteSize;
		}, outStream);
		byte[] output = outStream.toByteArray();
		Assert.assertEquals("video/webm", outMime[0]);
		Assert.assertEquals(data.length, (int)outByteSize[0]);
		Assert.assertArrayEquals(data, output);
	}

	@Test
	public void testProcessVideo() throws Throwable
	{
		IConfigFileSystem files = _getTestingDraftFiles();
		DraftManager draftManager = new DraftManager(files.getDraftsTopLevelDirectory());
		int id = 1;
		InteractiveHelpers.createNewDraft(draftManager, id);
		
		// Save the video content.
		byte[] data = "Testing video".getBytes();
		VideoSaver saver = InteractiveHelpers.openNewVideo(draftManager, id);
		InteractiveHelpers.appendToNewVideo(saver, data, 0, data.length);
		InteractiveHelpers.closeNewVideo(saver, "video/webm", 5, 6);
		
		// Process it.
		long[] outSize = new long[1];
		String[] outError = new String[1];
		CountDownLatch latch = new CountDownLatch(1);
		VideoProcessor processor = InteractiveHelpers.openVideoProcessor(new VideoProcessor.ProcessWriter()
		{
			@Override
			public void totalBytesProcessed(long bytesProcessed)
			{
				outSize[0] = bytesProcessed;
			}
			@Override
			public void processingError(String error)
			{
				outError[0] = error;
			}
			@Override
			public void processingDone(long outputSizeBytes)
			{
				latch.countDown();
			}
		}, draftManager, id, "tr \"eio\" \"XYZ\"");
		
		// This is done in a background thread so wait for it to finish.
		latch.await();
		InteractiveHelpers.closeVideoProcessor(processor);
		Assert.assertEquals(13, outSize[0]);
		Assert.assertNull(outError[0]);
		
		// Re-read it.
		String[] outMime = new String[1];
		long[] outByteSize = new long[1];
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		InteractiveHelpers.writeProcessedVideoToStream(draftManager, id, (String mime, Long byteSize) -> {
			outMime[0] = mime;
			outByteSize[0] = byteSize;
		}, outStream);
		Assert.assertEquals("video/webm", outMime[0]);
		Assert.assertEquals(data.length, (int)outByteSize[0]);
		byte[] expected = "TXstYng vYdXZ".getBytes();
		byte[] output = outStream.toByteArray();
		Assert.assertArrayEquals(expected, output);
		
		// Verify that we can delete these videos.
		Assert.assertTrue(InteractiveHelpers.deleteOriginalVideo(draftManager, id));
		try
		{
			InteractiveHelpers.writeOriginalVideoToStream(draftManager, id, (String mime, Long byteSize) -> {
				// This shouldn't be called.
				Assert.fail();
			}, outStream);
			Assert.fail();
		}
		catch (FileNotFoundException e)
		{
			// Expected.
		}
		Assert.assertTrue(InteractiveHelpers.deleteProcessedVideo(draftManager, id));
		try
		{
			InteractiveHelpers.writeProcessedVideoToStream(draftManager, id, (String mime, Long byteSize) -> {
				// This shouldn't be called.
				Assert.fail();
			}, outStream);
			Assert.fail();
		}
		catch (FileNotFoundException e)
		{
			// Expected.
		}
	}


	private static IConfigFileSystem _getTestingDraftFiles() throws IOException
	{
		File directory = FOLDER.newFolder();
		return new RealConfigFileSystem(directory);
	}
}
