package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;

import com.jeffdisher.cacophony.commands.CommandRunner;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.FutureCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.data.local.v4.Draft;
import com.jeffdisher.cacophony.data.local.v4.DraftManager;
import com.jeffdisher.cacophony.data.local.v4.IDraftWrapper;
import com.jeffdisher.cacophony.data.local.v4.SizedElement;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.MiscHelpers;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * These are very simple helpers used by the InteractiveServer, only pulled out that they can be understood and tested
 * in a high-level way, without needing to also test the REST server at the same time.
 */
public class InteractiveHelpers
{
	private static final String XSRF = "XSRF";
	private static final String LOCAL_IP = "127.0.0.1";

	/**
	 * Updates the information about the original video in the given draft.  This is called after the video has been
	 * saved.
	 * 
	 * @param openDraft The draft to update.
	 * @param mime The MIME type of the video.
	 * @param height The height, in pixels, of the video.
	 * @param width The width, in pixels, of the video.
	 * @param savedFileSizeBytes The total size of the video, in bytes.
	 */
	public static void updateOriginalVideo(IDraftWrapper openDraft, String mime, int height, int width, long savedFileSizeBytes)
	{
		SizedElement originalVideo = new SizedElement(mime, height, width, savedFileSizeBytes);
		openDraft.updateDraftUnderLock((Draft oldDraft) ->
			new Draft(oldDraft.id()
					, oldDraft.publishedSecondsUtc()
					, oldDraft.title()
					, oldDraft.description()
					, oldDraft.discussionUrl()
					, oldDraft.thumbnail()
					, originalVideo
					, oldDraft.processedVideo()
					, oldDraft.audio()
					, oldDraft.replyTo()
			)
		);
	}
	/**
	 * Updates the information about the audio in the given draft.  This is called after the audio has been saved.
	 * 
	 * @param openDraft The draft to update.
	 * @param mime The MIME type of the audio.
	 * @param savedFileSizeBytes The total size of the audio, in bytes.
	 */
	public static void updateAudio(IDraftWrapper openDraft, String mime, long savedFileSizeBytes)
	{
		SizedElement audio = new SizedElement(mime, 0, 0, savedFileSizeBytes);
		openDraft.updateDraftUnderLock((Draft oldDraft) ->
			new Draft(oldDraft.id()
					, oldDraft.publishedSecondsUtc()
					, oldDraft.title()
					, oldDraft.description()
					, oldDraft.discussionUrl()
					, oldDraft.thumbnail()
					, oldDraft.originalVideo()
					, oldDraft.processedVideo()
					, audio
					, oldDraft.replyTo()
			)
		);
	}

	/**
	 * Starts the video processor to convert the original video associated with the draftId using the given
	 * processCommand.
	 * 
	 * @param session The session to use when updating the progress of the processing.
	 * @param draftManager The draft manager.
	 * @param draftId The draft ID to open.
	 * @param processCommand The processing command to use.
	 * @return The video processor for this operation.
	 * @throws FileNotFoundException If the draft doesn't exist.
	 * @throws IOException If the background process fails to start.
	 */
	public static VideoProcessor openVideoProcessor(VideoProcessor.ProcessWriter session, DraftManager draftManager, int draftId, String processCommand) throws FileNotFoundException, IOException
	{
		return new VideoProcessor(session, draftManager, draftId, processCommand);
	}
	/**
	 * Called to stop the video processing operation.
	 * 
	 * @param processor The processor to stop.
	 */
	public static void closeVideoProcessor(VideoProcessor processor)
	{
		processor.stopProcess();
	}

	/**
	 * Fetches a list of all drafts known to the given manager.
	 * 
	 * @param draftManager The manager to query.
	 * @return The list of draft objects.
	 */
	public static List<Draft> listDrafts(DraftManager draftManager)
	{
		return draftManager.listAllDrafts();
	}
	/**
	 * Creates a new draft with the given ID.
	 * 
	 * @param draftManager The manager.
	 * @param draftId The new draft ID to create.
	 * @param replyTo The CID of another post on the network, if this is a reply (usually null).
	 * @return The new draft object.
	 */
	public static Draft createNewDraft(DraftManager draftManager, int draftId, IpfsFile replyTo)
	{
		return draftManager.createNewDraft(draftId, replyTo).loadDraft();
	}
	/**
	 * Reads data around an existing draft.
	 * 
	 * @param draftManager The manager.
	 * @param draftId The ID to read.
	 * @return The new draft object, null if the ID is unknown.
	 */
	public static Draft readExistingDraft(DraftManager draftManager, int draftId)
	{
		IDraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		Draft loaded = null;
		if (null != wrapper)
		{
			loaded = wrapper.loadDraft();
		}
		return loaded;
	}
	/**
	 * Updates a single draft.  Any of parameters being non-null will mean we will update them (discussionUrl is a
	 * special-case where empty string will mean "remove").
	 * 
	 * @param draftManager The manager.
	 * @param draftId The draft ID.
	 * @param title The new title, if not null.
	 * @param description The new description, if not null.
	 * @param discussionUrl The new discussion URL, if not null (empty means "remove").
	 * @return The newly updated draft, null if it wasn't found.
	 */
	public static Draft updateDraftText(DraftManager draftManager, int draftId, String title, String description, String discussionUrl)
	{
		IDraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		Draft finalDraft = null;
		if (null != wrapper)
		{
			finalDraft = wrapper.updateDraftUnderLock((Draft oldDraft) ->
			{
				String newTitle = (null != title)
						? title
						: oldDraft.title()
				;
				String newDescription = (null != description)
						? description
						: oldDraft.description()
				;
				String newDiscussionUrl = (null != discussionUrl)
						? (discussionUrl.isEmpty() ? null : discussionUrl)
						: oldDraft.discussionUrl()
				;
				return new Draft(oldDraft.id()
						, oldDraft.publishedSecondsUtc()
						, newTitle
						, newDescription
						, newDiscussionUrl
						, oldDraft.thumbnail()
						, oldDraft.originalVideo()
						, oldDraft.processedVideo()
						, oldDraft.audio()
						, oldDraft.replyTo()
				);
			});
		}
		return finalDraft;
	}
	/**
	 * Deletes an existing draft.
	 * 
	 * @param draftManager The manager.
	 * @param draftId The ID to delete.
	 * @return True if the draft was deleted or false if the draft couldn't be deleted due to existing readers or
	 * writers.
	 * @throws FileNotFoundException The draft is unknown.
	 */
	public static boolean deleteExistingDraft(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		return draftManager.deleteExistingDraft(draftId);
	}

	/**
	 * Loads a thumbnail from disk and writes it to the given outStream.
	 * 
	 * @param draftManager The manager.
	 * @param draftId The ID of the draft to read.
	 * @param mimeConsumer The consumer which will be given the MIME type.
	 * @param outStream The stream which will receive the data.
	 * @throws FileNotFoundException The draft wasn't found or it had no thumbnail.
	 * @throws IOException There was an error writing to the stream.
	 */
	public static void loadThumbnailToStream(DraftManager draftManager, int draftId, Consumer<String> mimeConsumer, OutputStream outStream) throws FileNotFoundException, IOException
	{
		IDraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		if (null == wrapper)
		{
			throw new FileNotFoundException();
		}
		try (InputStream input = wrapper.readThumbnail())
		{
			if (null == input)
			{
				throw new FileNotFoundException();
			}
			mimeConsumer.accept(wrapper.loadDraft().thumbnail().mime());
			MiscHelpers.copyToEndOfFile(input, outStream);
		}
	}
	/**
	 * Saves a new thumbnail from the given inStream to disk for the given draft, replacing an existing one if it was
	 * already there.  Also updates the draft to describe the thumbnail.
	 * 
	 * @param draftManager The manager.
	 * @param draftId The ID of the draft to modify.
	 * @param height The height of the image, in pixels
	 * @param width The width of the image, in pixels.
	 * @param mime The MIME type of the image.
	 * @param inStream The stream which contains the raw image data.
	 * @throws FileNotFoundException The draft doesn't exist.
	 * @throws IOException There was an error reading the bytes from the network.
	 */
	public static void saveThumbnailFromStream(DraftManager draftManager, int draftId, int height, int width, String mime, InputStream inStream) throws FileNotFoundException, IOException
	{
		IDraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		if (null == wrapper)
		{
			throw new FileNotFoundException();
		}
		long bytesCopied = 0L;
		try (OutputStream output = wrapper.writeThumbnail())
		{
			bytesCopied = MiscHelpers.copyToEndOfFile(inStream, output);
		}
		Assert.assertTrue(bytesCopied > 0L);
		SizedElement thumbnail = new SizedElement(mime, height, width, bytesCopied);
		wrapper.updateDraftUnderLock((Draft oldDraft) ->
			new Draft(oldDraft.id()
					, oldDraft.publishedSecondsUtc()
					, oldDraft.title()
					, oldDraft.description()
					, oldDraft.discussionUrl()
					, thumbnail
					, oldDraft.originalVideo()
					, oldDraft.processedVideo()
					, oldDraft.audio()
					, oldDraft.replyTo()
			)
		);
	}

	/**
	 * Deletes the original video from the draft.
	 * @param draftManager The DraftManager.
	 * @param draftId The draft to open when searching for the video.
	 * @return True if this was deleted or false if it couldn't be or there was no video file.
	 * @throws FileNotFoundException The draft doesn't exist.
	 */
	public static boolean deleteOriginalVideo(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		IDraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		if (null == wrapper)
		{
			throw new FileNotFoundException();
		}
		boolean didDelete = wrapper.deleteOriginalVideo();
		if (didDelete)
		{
			wrapper.updateDraftUnderLock((Draft oldDraft) -> 
				new Draft(oldDraft.id()
						, oldDraft.publishedSecondsUtc()
						, oldDraft.title()
						, oldDraft.description()
						, oldDraft.discussionUrl()
						, oldDraft.thumbnail()
						, null
						, oldDraft.processedVideo()
						, oldDraft.audio()
						, oldDraft.replyTo()
				)
			);
		}
		return didDelete;
	}
	/**
	 * Deletes the processed video from the draft.
	 * @param draftManager The DraftManager.
	 * @param draftId The draft to open when searching for the video.
	 * @return True if this was deleted or false if it couldn't be or there was no video file.
	 * @throws FileNotFoundException The draft doesn't exist.
	 */
	public static boolean deleteProcessedVideo(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		IDraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		if (null == wrapper)
		{
			throw new FileNotFoundException();
		}
		boolean didDelete = wrapper.deleteProcessedVideo();
		if (didDelete)
		{
			wrapper.updateDraftUnderLock((Draft oldDraft) -> 
				new Draft(oldDraft.id()
						, oldDraft.publishedSecondsUtc()
						, oldDraft.title()
						, oldDraft.description()
						, oldDraft.discussionUrl()
						, oldDraft.thumbnail()
						, oldDraft.originalVideo()
						, null
						, oldDraft.audio()
						, oldDraft.replyTo()
				)
			);
		}
		return didDelete;
	}
	/**
	 * Deletes the audio from the draft.
	 * @param draftManager The DraftManager.
	 * @param draftId The draft to open when searching for the audio.
	 * @return True if this was deleted or false if it couldn't be or there was no audio file.
	 * @throws FileNotFoundException The draft doesn't exist.
	 */
	public static boolean deleteAudio(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		IDraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		if (null == wrapper)
		{
			throw new FileNotFoundException();
		}
		boolean didDelete = wrapper.deleteAudio();
		if (didDelete)
		{
			wrapper.updateDraftUnderLock((Draft oldDraft) -> 
				new Draft(oldDraft.id()
						, oldDraft.publishedSecondsUtc()
						, oldDraft.title()
						, oldDraft.description()
						, oldDraft.discussionUrl()
						, oldDraft.thumbnail()
						, oldDraft.originalVideo()
						, oldDraft.processedVideo()
						, null
						, oldDraft.replyTo()
				)
			);
		}
		return didDelete;
	}
	/**
	 * Deletes the thumbnail image from the draft.
	 * @param draftManager The DraftManager.
	 * @param draftId The draft to open when searching for the video.
	 * @return True if this was deleted or false if it couldn't be or there was no thumbnail file.
	 * @throws FileNotFoundException The draft doesn't exist.
	 */
	public static boolean deleteThumbnail(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		IDraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		if (null == wrapper)
		{
			throw new FileNotFoundException();
		}
		boolean didDelete = wrapper.deleteThumbnail();
		if (didDelete)
		{
			wrapper.updateDraftUnderLock((Draft oldDraft) -> 
				new Draft(oldDraft.id()
						, oldDraft.publishedSecondsUtc()
						, oldDraft.title()
						, oldDraft.description()
						, oldDraft.discussionUrl()
						, null
						, oldDraft.originalVideo()
						, oldDraft.processedVideo()
						, oldDraft.audio()
						, oldDraft.replyTo()
				)
			);
		}
		return didDelete;
	}

	/**
	 * Checks that the request is from the local IP and has the expected XSRF cookie.  Sets the error in the response if
	 * not.
	 * 
	 * @param xsrf The XSRF cookie value we expect to see.
	 * @param request The HTTP request.
	 * @param response The response object.
	 * @return True if this request should proceed or false if it should be rejected.
	 */
	public static boolean verifySafeRequest(String xsrf, HttpServletRequest request, HttpServletResponse response)
	{
		boolean isSafe = false;
		// We now only bind the local IP so another address should be impossible.
		Assert.assertTrue(LOCAL_IP.equals(request.getRemoteAddr()));
		String value = null;
		Cookie[] cookies = request.getCookies();
		if (null != cookies)
		{
			for (Cookie cookie : cookies)
			{
				if (XSRF.equals(cookie.getName()))
				{
					value = cookie.getValue();
				}
			}
		}
		if (xsrf.equals(value))
		{
			// This means all checks passed.
			isSafe = true;
		}
		else
		{
			isSafe = false;
			System.err.println("Invalid XSRF: \"" + value + "\"");
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		}
		return isSafe;
	}

	/**
	 * Checks that the XSRF cookie is correct in this upgrade request.
	 * 
	 * @param xsrf The XSRF cookie value we expect to see.
	 * @param upgradeRequest The upgrade request.
	 * @return True if this request should proceed or false if it should be rejected.
	 */
	public static boolean verifySafeWebSocket(String xsrf, JettyServerUpgradeRequest upgradeRequest)
	{
		String rawDescription = upgradeRequest.getRemoteSocketAddress().toString();
		// This rawDescription looks like "/127.0.0.1:65657" so we need to parse it.
		String ip = rawDescription.substring(1).split(":")[0];
		// We now only bind the local IP so another address should be impossible.
		Assert.assertTrue(LOCAL_IP.equals(ip));
		String value = upgradeRequest.getCookies().stream().filter((cookie) -> XSRF.equals(cookie.getName())).map((cookie) -> cookie.getValue()).findFirst().get();
		return xsrf.equals(value);
	}

	/**
	 * A complex helper which handles all of the processing of an ICommand instance within a REST end-point.  This will
	 * run the command, handling any exceptions as the appropriate HTTP errors, and will return the given result object
	 * from the command for further processing.
	 * 
	 * @param <T> The return value of the command.
	 * @param response Used for setting HTTP result codes (even in the success case).
	 * @param runner The CommandRunner to execute the command.
	 * @param blockingKey The key to use as the blocking key for this command (null for no blocking).
	 * @param command The command to run.
	 * @param overrideKey If non-null, will be used to find the key name for the command's context.
	 * @return A wrapper of the object returned by the command and the context where it executed.
	 * @throws IOException There was an error interacting with the response object.
	 */
	public static <T extends ICommand.Result> SuccessfulCommand<T> runCommandAndHandleErrors(HttpServletResponse response, CommandRunner runner, IpfsKey blockingKey, ICommand<T> command, IpfsKey overrideKey) throws IOException
	{
		SuccessfulCommand<T> result = null;
		try
		{
			FutureCommand<T> future = (null != blockingKey)
					? runner.runBlockedCommand(blockingKey, command, overrideKey)
					: runner.runCommand(command, overrideKey)
			;
			// These always return a future.
			Assert.assertTrue(null != future);
			T output = future.get();
			// The commands should only fail with exceptions, always returning non-null on success.
			Assert.assertTrue(null != output);
			result = new SuccessfulCommand<T>(output, future.context);
			response.setStatus(HttpServletResponse.SC_OK);
		}
		catch (IpfsConnectionException e)
		{
			// An internal network error.
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		catch (UsageException e)
		{
			// The parameters were wrong.
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
		catch (KeyException e)
		{
			// We couldn't resolve the key.
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
		catch (ProtocolDataException e)
		{
			// We found the requeste data but it was corrupt or otherwise not obeying protocol rules.
			response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
		}
		catch (VersionException e)
		{
			// This should never appear at the top-level.
			throw Assert.unexpected(e);
		}
		catch (CacophonyException e)
		{
			// We should have hit one of the above cases.  This would mean we added another exception type but didn't handle it.
			throw Assert.unexpected(e);
		}
		return result;
	}


	/**
	 * A common class used to communicate back the result of an internal command to the caller.
	 *
	 * @param <T> The type of data returned by the underlying command being run.
	 */
	public static record SuccessfulCommand<T extends ICommand.Result>(T result, Context context) {}
}
