package com.jeffdisher.cacophony.interactive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.DataDomain;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.local.IConfigFileSystem;
import com.jeffdisher.cacophony.data.local.RealConfigFileSystem;
import com.jeffdisher.cacophony.data.local.v4.Draft;
import com.jeffdisher.cacophony.data.local.v4.DraftManager;
import com.jeffdisher.cacophony.data.local.v4.IDraftWrapper;
import com.jeffdisher.cacophony.data.local.v4.SizedElement;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.SilentLogger;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestInteractiveHelpers
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME = "keyName";


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
		InteractiveHelpers.createNewDraft(draftManager, 1, null);
		InteractiveHelpers.createNewDraft(draftManager, 2, null);
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
		InteractiveHelpers.createNewDraft(draftManager, id, null);
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
		InteractiveHelpers.createNewDraft(draftManager, id, null);
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
		InteractiveHelpers.createNewDraft(draftManager, id, null);
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
	public void testProcessVideo() throws Throwable
	{
		IConfigFileSystem files = _getTestingDraftFiles();
		DraftManager draftManager = new DraftManager(files.getDraftsTopLevelDirectory());
		int id = 1;
		InteractiveHelpers.createNewDraft(draftManager, id, null);
		
		// Save the video content.
		byte[] data = "Testing video".getBytes();
		IDraftWrapper openDraft = draftManager.openExistingDraft(id);
		try (OutputStream out = openDraft.writeOriginalVideo())
		{
			out.write(data);
		}
		InteractiveHelpers.updateOriginalVideo(openDraft, "video/webm", 5, 6, data.length);
		
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
		IDraftWrapper wrapper = draftManager.openExistingDraft(id);
		byte[] output = null;
		try (InputStream stream = wrapper.readProcessedVideo())
		{
			output = stream.readAllBytes();
		}
		byte[] expected = "TXstYng vYdXZ".getBytes();
		Assert.assertArrayEquals(expected, output);
		SizedElement processedSizedElement = wrapper.loadDraft().processedVideo();
		Assert.assertEquals("video/webm", processedSizedElement.mime());
		Assert.assertEquals(expected.length, processedSizedElement.byteSize());
		Assert.assertEquals(5, processedSizedElement.height());
		Assert.assertEquals(6, processedSizedElement.width());
		
		// Verify that we can delete these videos.
		Assert.assertTrue(InteractiveHelpers.deleteOriginalVideo(draftManager, id));
		Assert.assertFalse(new File(new File(files.getDraftsTopLevelDirectory(), "draft_1"), "original_video.webm").exists());
		Assert.assertTrue(InteractiveHelpers.deleteProcessedVideo(draftManager, id));
		Assert.assertFalse(new File(new File(files.getDraftsTopLevelDirectory(), "draft_1"), "processed_video.webm").exists());
	}

	@Test
	public void testPublish() throws Throwable
	{
		// Make sure that the directory doesn't exist.
		IConfigFileSystem fileSystem = new RealConfigFileSystem(new File(FOLDER.newFolder(), "sub"));
		MockSwarm swarm = new MockSwarm();
		MockSingleNode connection = new MockSingleNode(swarm);
		connection.addNewKey(KEY_NAME, MockKeys.K1);
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(connection, 1);
		SilentLogger logger = new SilentLogger();
		
		// First, create a channel so the channel is set up.
		Context context = new Context(new DraftManager(fileSystem.getDraftsTopLevelDirectory())
				, model
				, connection
				, scheduler
				, () -> System.currentTimeMillis()
				, logger
				, DataDomain.FAKE_BASE_URL
				, null
				, null
				, null
				, new CacheUpdater(null, null, null, null, null)
				, null
				, null
		);
		new CreateChannelCommand(KEY_NAME).runInContext(context);
		
		// Now, create a basic draft.
		DraftManager draftManager = new DraftManager(fileSystem.getDraftsTopLevelDirectory());
		int id = 1;
		InteractiveHelpers.createNewDraft(draftManager, id, null);
		InteractiveHelpers.updateDraftText(draftManager, id, "title", "description", null);
		
		// Publish the draft.
		try (IWritingAccess access = Context.writeAccess(context))
		{
			PublishBuilder builder = new PublishBuilder();
			draftManager.prepareToPublishDraft(builder, id, true, false);
			PublishCommand publish = builder.getCommand();
			OnePost result = publish.runInContext(context);
			access.beginIndexPublish(result.getIndexToPublish());
		}
		
		// Verify the data is on the node.
		AbstractRecord record = null;
		IpfsFile firstRecord = null;
		for (IpfsFile file : connection.getStoredFileSet())
		{
			try
			{
				record = AbstractRecord.DESERIALIZER.apply(connection.loadData(file));
				Assert.assertNull(firstRecord);
				firstRecord = file;
			}
			catch (FailedDeserializationException e)
			{
				// This is a failure to parse.
			}
		}
		Assert.assertEquals("title", record.getName());
		Assert.assertNull(record.getVideoExtension());
		
		// Now, post a second entry with a video and make sure we see the attachment.
		id = 2;
		InteractiveHelpers.createNewDraft(draftManager, id, null);
		InteractiveHelpers.updateDraftText(draftManager, id, "title2", "description", null);
		byte[] data = "Testing video".getBytes();
		IDraftWrapper openDraft = draftManager.openExistingDraft(id);
		try (OutputStream out = openDraft.writeOriginalVideo())
		{
			out.write(data);
		}
		InteractiveHelpers.updateOriginalVideo(openDraft, "video/webm", 5, 6, data.length);
		
		// Publish the draft.
		try (IWritingAccess access = Context.writeAccess(context))
		{
			PublishBuilder builder = new PublishBuilder();
			draftManager.prepareToPublishDraft(builder, id, true, false);
			PublishCommand publish = builder.getCommand();
			OnePost result = publish.runInContext(context);
			access.beginIndexPublish(result.getIndexToPublish());
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
					record = AbstractRecord.DESERIALIZER.apply(connection.loadData(file));
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
		Assert.assertEquals(1, record.getVideoExtension().size());
		
		// Now, publish another entry but explicitly forbid attaching the video.
		id = 3;
		InteractiveHelpers.createNewDraft(draftManager, id, null);
		InteractiveHelpers.updateDraftText(draftManager, id, "title3", "description", null);
		openDraft = draftManager.openExistingDraft(id);
		try (OutputStream out = openDraft.writeOriginalVideo())
		{
			out.write(data);
		}
		InteractiveHelpers.updateOriginalVideo(openDraft, "video/webm", 5, 6, data.length);
		
		// Publish the draft WITHOUT uploading the video attachment.
		try (IWritingAccess access = Context.writeAccess(context))
		{
			PublishBuilder builder = new PublishBuilder();
			draftManager.prepareToPublishDraft(builder, id, false, false);
			PublishCommand publish = builder.getCommand();
			OnePost result = publish.runInContext(context);
			access.beginIndexPublish(result.getIndexToPublish());
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
					record = AbstractRecord.DESERIALIZER.apply(connection.loadData(file));
				}
				catch (FailedDeserializationException e)
				{
					// This is a failure to parse.
				}
			}
		}
		Assert.assertEquals("title3", record.getName());
		Assert.assertNull(record.getVideoExtension());
		scheduler.shutdown();
	}

	@Test
	public void testPublishAudio() throws Throwable
	{
		// Make sure that the directory doesn't exist.
		IConfigFileSystem fileSystem = new RealConfigFileSystem(new File(FOLDER.newFolder(), "sub"));
		MockSwarm swarm = new MockSwarm();
		MockSingleNode connection = new MockSingleNode(swarm);
		connection.addNewKey(KEY_NAME, MockKeys.K1);
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(connection, 1);
		SilentLogger logger = new SilentLogger();
		
		// First, create a channel so the channel is set up.
		Context context = new Context(new DraftManager(fileSystem.getDraftsTopLevelDirectory())
				, model
				, connection
				, scheduler
				, () -> System.currentTimeMillis()
				, logger
				, DataDomain.FAKE_BASE_URL
				, null
				, null
				, null
				, new CacheUpdater(null, null, null, null, null)
				, null
				, null
		);
		new CreateChannelCommand(KEY_NAME).runInContext(context);
		
		// Now, create a draft and attach audio.
		DraftManager draftManager = new DraftManager(fileSystem.getDraftsTopLevelDirectory());
		int id = 1;
		InteractiveHelpers.createNewDraft(draftManager, id, null);
		InteractiveHelpers.updateDraftText(draftManager, id, "title", "description", null);
		IDraftWrapper openDraft = draftManager.openExistingDraft(id);
		byte[] data = "Testing audio".getBytes();
		try (OutputStream out = openDraft.writeAudio())
		{
			out.write(data);
		}
		InteractiveHelpers.updateAudio(openDraft, "audio/ogg", data.length);

		// Publish the draft.
		try (IWritingAccess access = Context.writeAccess(context))
		{
			PublishBuilder builder = new PublishBuilder();
			draftManager.prepareToPublishDraft(builder, id, false, true);
			PublishCommand publish = builder.getCommand();
			OnePost result = publish.runInContext(context);
			access.beginIndexPublish(result.getIndexToPublish());
		}
		
		// Verify the data is on the node.
		AbstractRecord record = null;
		IpfsFile firstRecord = null;
		for (IpfsFile file : connection.getStoredFileSet())
		{
			try
			{
				record = AbstractRecord.DESERIALIZER.apply(connection.loadData(file));
				Assert.assertNull(firstRecord);
				firstRecord = file;
			}
			catch (FailedDeserializationException e)
			{
				// This is a failure to parse.
			}
		}
		Assert.assertEquals("title", record.getName());
		Assert.assertEquals(1, record.getVideoExtension().size());
		Assert.assertEquals("audio/ogg", record.getVideoExtension().get(0).mime());
		scheduler.shutdown();
	}


	private static IConfigFileSystem _getTestingDraftFiles() throws IOException
	{
		File directory = FOLDER.newFolder();
		return new RealConfigFileSystem(directory);
	}


	private static class PublishBuilder implements DraftManager.IPublishBuilder
	{
		private final List<ElementSubCommand> _subCommands = new ArrayList<>();
		private PublishCommand _finishedCommand;
		
		@Override
		public void attach(String mime, File filePath, int height, int width)
		{
			_subCommands.add(new ElementSubCommand(mime, filePath, height, width));
		}
		@Override
		public void complete(String name, String description, String discussionUrl, IpfsFile replyTo, String thumbnailMime, File thumbnailPath)
		{
			Assert.assertTrue(null == _finishedCommand);
			ElementSubCommand[] attachments = _subCommands.toArray((int size) -> new ElementSubCommand[size]);
			_finishedCommand = new PublishCommand(name, description, discussionUrl, replyTo, thumbnailMime, thumbnailPath, attachments);
		}
		public PublishCommand getCommand()
		{
			Assert.assertTrue(null != _finishedCommand);
			return _finishedCommand;
		}
	}
}
