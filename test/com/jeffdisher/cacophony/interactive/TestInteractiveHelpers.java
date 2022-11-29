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

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.logic.RealConfigFileSystem;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.testutils.MockConnectionFactory;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestInteractiveHelpers
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");


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
		String mimeArg = "namelessMime";
		String string = "test data";
		InteractiveHelpers.createNewDraft(draftManager, id);
		ByteArrayInputStream input = new ByteArrayInputStream(string.getBytes());
		InteractiveHelpers.saveThumbnailFromStream(draftManager, id, height, width, mimeArg, input);
		
		String[] outString = new String[1];
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		InteractiveHelpers.loadThumbnailToStream(draftManager, id, (String mime) -> outString[0] = mime, outStream);
		Assert.assertEquals(mimeArg, outString[0]);
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

	@Test
	public void testPublish() throws Throwable
	{
		// Make sure that the directory doesn't exist.
		IConfigFileSystem fileSystem = new RealConfigFileSystem(new File(FOLDER.newFolder(), "sub"));
		MockSingleNode connection = new MockSingleNode();
		connection.addNewKey(KEY_NAME, PUBLIC_KEY);
		MockConnectionFactory connectionFactory = new MockConnectionFactory(connection);
		StandardEnvironment env = new StandardEnvironment(System.out, fileSystem, connectionFactory, true);
		
		// First, create a channel so the channel is set up.
		new CreateChannelCommand(IPFS_HOST, KEY_NAME).runInEnvironment(env);
		
		// Now, create a basic draft.
		DraftManager draftManager = new DraftManager(fileSystem.getDraftsTopLevelDirectory());
		int id = 1;
		InteractiveHelpers.createNewDraft(draftManager, id);
		InteractiveHelpers.updateDraftText(draftManager, id, "title", "description", null);
		
		// Publish the draft.
		try (IWritingAccess access = StandardAccess.writeAccess(env))
		{
			InteractiveHelpers.publishExistingDraft(env, access, draftManager, id, true);
		}
		
		// Verify the data is on the node.
		StreamRecord record = null;
		IpfsFile firstRecord = null;
		for (IpfsFile file : connection.getStoredFileSet())
		{
			try
			{
				record = GlobalData.deserializeRecord(connection.loadData(file));
				Assert.assertNull(firstRecord);
				firstRecord = file;
			}
			catch (FailedDeserializationException e)
			{
				// This is a failure to parse.
			}
		}
		Assert.assertEquals("title", record.getName());
		Assert.assertEquals(0, record.getElements().getElement().size());
		
		// Now, post a second entry with a video and make sure we see the attachment.
		id = 2;
		InteractiveHelpers.createNewDraft(draftManager, id);
		InteractiveHelpers.updateDraftText(draftManager, id, "title2", "description", null);
		byte[] data = "Testing video".getBytes();
		VideoSaver saver = InteractiveHelpers.openNewVideo(draftManager, id);
		InteractiveHelpers.appendToNewVideo(saver, data, 0, data.length);
		InteractiveHelpers.closeNewVideo(saver, "video/webm", 5, 6);
		
		// Publish the draft.
		try (IWritingAccess access = StandardAccess.writeAccess(env))
		{
			InteractiveHelpers.publishExistingDraft(env, access, draftManager, id, true);
		}
		
		// Verify that we see both.
		record = null;
		boolean didSeeFirst = false;
		IpfsFile secondRecord = null;
		for (IpfsFile file : connection.getStoredFileSet())
		{
			if (firstRecord.equals(file))
			{
				didSeeFirst = true;
			}
			else
			{
				try
				{
					record = GlobalData.deserializeRecord(connection.loadData(file));
					Assert.assertNull(secondRecord);
					secondRecord = file;
				}
				catch (FailedDeserializationException e)
				{
					// This is a failure to parse.
				}
			}
		}
		Assert.assertTrue(didSeeFirst);
		Assert.assertEquals("title2", record.getName());
		Assert.assertEquals(1, record.getElements().getElement().size());
		
		// Now, publish another entry but explicitly forbid attaching the video.
		id = 3;
		InteractiveHelpers.createNewDraft(draftManager, id);
		InteractiveHelpers.updateDraftText(draftManager, id, "title3", "description", null);
		saver = InteractiveHelpers.openNewVideo(draftManager, id);
		InteractiveHelpers.appendToNewVideo(saver, data, 0, data.length);
		InteractiveHelpers.closeNewVideo(saver, "video/webm", 5, 6);
		
		// Publish the draft WITHOUT uploading the video attachment.
		try (IWritingAccess access = StandardAccess.writeAccess(env))
		{
			InteractiveHelpers.publishExistingDraft(env, access, draftManager, id, false);
		}
		
		// Verify that the new entry has no attachments.
		record = null;
		for (IpfsFile file : connection.getStoredFileSet())
		{
			if (firstRecord.equals(file) || secondRecord.equals(file))
			{
			}
			else
			{
				try
				{
					record = GlobalData.deserializeRecord(connection.loadData(file));
				}
				catch (FailedDeserializationException e)
				{
					// This is a failure to parse.
				}
			}
		}
		Assert.assertEquals("title3", record.getName());
		Assert.assertEquals(0, record.getElements().getElement().size());
	}


	private static IConfigFileSystem _getTestingDraftFiles() throws IOException
	{
		File directory = FOLDER.newFolder();
		return new RealConfigFileSystem(directory);
	}
}
