package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jetty.websocket.api.Session;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.data.local.v1.SizedElement;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IDraftWrapper;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.PublishHelpers;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
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

	// --- Methods related to saving the new video.
	public static void updateOriginalVideo(IDraftWrapper openDraft, String mime, int height, int width, long savedFileSizeBytes)
	{
		SizedElement originalVideo = new SizedElement(mime, height, width, savedFileSizeBytes);
		openDraft.updateDraftUnderLock((Draft oldDraft) ->
			new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), originalVideo, oldDraft.processedVideo(), oldDraft.audio())
		);
	}
	public static void updateAudio(IDraftWrapper openDraft, String mime, long savedFileSizeBytes)
	{
		SizedElement audio = new SizedElement(mime, 0, 0, savedFileSizeBytes);
		openDraft.updateDraftUnderLock((Draft oldDraft) ->
			new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), oldDraft.originalVideo(), oldDraft.processedVideo(), audio)
		);
	}

	// --- Methods related to processing the video (this is small since it mostly just invokes callbacks to the session on a different thread).
	public static VideoProcessor openVideoProcessor(VideoProcessor.ProcessWriter session, DraftManager draftManager, int draftId, String processCommand) throws IOException
	{
		return new VideoProcessor(session, draftManager, draftId, processCommand);
	}
	public static void closeVideoProcessor(VideoProcessor processor)
	{
		processor.stopProcess();
	}

	// --- Methods related to draft management.
	public static List<Draft> listDrafts(DraftManager draftManager)
	{
		return draftManager.listAllDrafts();
	}
	public static Draft createNewDraft(DraftManager draftManager, int draftId) throws IOException
	{
		return draftManager.createNewDraft(draftId).loadDraft();
	}
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
				return new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), newTitle, newDescription, newDiscussionUrl, oldDraft.thumbnail(), oldDraft.originalVideo(), oldDraft.processedVideo(), oldDraft.audio());
			});
		}
		return finalDraft;
	}
	public static boolean deleteExistingDraft(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		return draftManager.deleteExistingDraft(draftId);
	}
	public static PublishHelpers.PublishResult postExistingDraft(ILogger logger
			, IWritingAccess access
			, DraftManager draftManager
			, int draftId
			, boolean shouldPublishVideo
			, boolean shouldPublishAudio
	) throws FileNotFoundException
	{
		IDraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		if (null == wrapper)
		{
			throw new FileNotFoundException();
		}
		Draft draft = wrapper.loadDraft();
		SizedElement video = null;
		InputStream videoInput = null;
		if (shouldPublishVideo)
		{
			video = draft.processedVideo();
			videoInput = wrapper.readProcessedVideo();
			if (null == video)
			{
				Assert.assertTrue(null == videoInput);
				video = draft.originalVideo();
				videoInput = wrapper.readOriginalVideo();
			}
		}
		SizedElement audio = null;
		InputStream audioInput = null;
		if (shouldPublishAudio)
		{
			audio = draft.audio();
			audioInput = wrapper.readAudio();
		}
		SizedElement thumbnail = draft.thumbnail();
		int elementCount = 0;
		if (null != thumbnail)
		{
			elementCount += 1;
		}
		if (null != video)
		{
			elementCount += 1;
		}
		if (null != audio)
		{
			elementCount += 1;
		}
		PublishHelpers.PublishElement[] subElements = new PublishHelpers.PublishElement[elementCount];
		int index = 0;
		if (null != thumbnail)
		{
			InputStream thumbnailInput = wrapper.readThumbnail();
			Assert.assertTrue(null != thumbnailInput);
			subElements[index] = new PublishHelpers.PublishElement(thumbnail.mime(), thumbnailInput, thumbnail.height(), thumbnail.width(), true);
			index += 1;
		}
		if (null != video)
		{
			Assert.assertTrue(null != videoInput);
			subElements[index] = new PublishHelpers.PublishElement(video.mime(), videoInput, video.height(), video.width(), false);
			index += 1;
		}
		if (null != audio)
		{
			Assert.assertTrue(null != audioInput);
			subElements[index] = new PublishHelpers.PublishElement(audio.mime(), audioInput, audio.height(), audio.width(), false);
			index += 1;
		}
		
		PublishHelpers.PublishResult result;
		ILogger log = logger.logStart("Publishing draft");
		try
		{
			// The draft can have empty strings or null (only due to old versions) for discussionURL but we want to pass null in those cases to not attach it.
			String discussionUrl = draft.discussionUrl();
			if ((null != discussionUrl) && discussionUrl.isEmpty())
			{
				discussionUrl = null;
			}
			result = PublishHelpers.uploadFileAndUpdateTracking(log, access, draft.title(), draft.description(), discussionUrl, subElements);
			log.logFinish("Publish success!");
		}
		catch (IpfsConnectionException e)
		{
			log.logFinish("Publish command failed with IpfsConnectionException: " + e.getLocalizedMessage());
			e.printStackTrace();
			// TODO:  Determine how to best handle a failure here.
			throw Assert.unexpected(e);
		}
		catch (SizeConstraintException e)
		{
			log.logFinish("Publish command failed due to an element being too large to store: " + e.getLocalizedMessage());
			e.printStackTrace();
			// This really shouldn't happen - it would mean that we were missing a static limit somewhere in the code.
			throw Assert.unexpected(e);
		}
		finally
		{
			closeElementFiles(subElements);
		}
		
		return result;
	}

	// --- Methods related to thumbnails.
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
			new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), thumbnail, oldDraft.originalVideo(), oldDraft.processedVideo(), oldDraft.audio())
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
				new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), null, oldDraft.processedVideo(), oldDraft.audio())
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
				new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), oldDraft.originalVideo(), null, oldDraft.audio())
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
				new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), oldDraft.originalVideo(), oldDraft.processedVideo(), null)
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
				new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), null, oldDraft.originalVideo(), oldDraft.processedVideo(), oldDraft.audio())
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
		if (LOCAL_IP.equals(request.getRemoteAddr()))
		{
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
		}
		else
		{
			isSafe = false;
			System.err.println("Invalid IP: " + request.getRemoteAddr());
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		}
		return isSafe;
	}
	/**
	 * Checks that the request is from the local IP and the upgrade request has the expected XSRF cookie.  Closes the
	 * session with an error if not.
	 * 
	 * @param xsrf The XSRF cookie value we expect to see.
	 * @param session The WebSocket session.
	 * @return True if this request should proceed or false if it should be rejected.
	 */
	public static boolean verifySafeWebSocket(String xsrf, Session session)
	{
		boolean isSafe = false;
		String rawDescription = session.getRemoteAddress().toString();
		// This rawDescription looks like "/127.0.0.1:65657" so we need to parse it.
		String ip = rawDescription.substring(1).split(":")[0];
		if (LOCAL_IP.equals(ip))
		{
			String value = session.getUpgradeRequest().getCookies().stream().filter((cookie) -> XSRF.equals(cookie.getName())).map((cookie) -> cookie.getValue()).findFirst().get();
			if (xsrf.equals(value))
			{
				// This means all checks passed.
				isSafe = true;
			}
			else
			{
				isSafe = false;
				System.err.println("Invalid XSRF: \"" + value + "\"");
				session.close(WebSocketCodes.SECURITY_FAILED, "Invalid XSRF");
			}
		}
		else
		{
			isSafe = false;
			System.err.println("Invalid IP: " + ip);
			session.close(WebSocketCodes.SECURITY_FAILED, "Invalid IP");
		}
		return isSafe;
	}

	/**
	 * A complex helper which handles all of the processing of an ICommand instance within a REST end-point.  This will
	 * run the command, handling any exceptions as the appropriate HTTP errors, and will return the given result object
	 * from the command for further processing.
	 * 
	 * @param <T> The return value of the command.
	 * @param response Used for setting HTTP result codes (even in the success case).
	 * @param context The context in which to run the command.
	 * @param command The command to run.
	 * @return The object returned by the command (null on failure).
	 * @throws IOException There was an error interacting with the response object.
	 */
	public static <T extends ICommand.Result> T runCommandAndHandleErrors(HttpServletResponse response, ICommand.Context context, ICommand<T> command) throws IOException
	{
		T result = null;
		try
		{
			result = command.runInContext(context);
			// The commands should only fail with exceptions, always returning non-null on success.
			Assert.assertTrue(null != result);
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


	private static void closeElementFiles(PublishHelpers.PublishElement[] elements)
	{
		for (PublishHelpers.PublishElement element : elements)
		{
			if (null != element)
			{
				InputStream file = element.fileData();
				try
				{
					file.close();
				}
				catch (IOException e)
				{
					// We don't know how this fails on close.
					throw Assert.unexpected(e);
				}
			}
		}
	}
}
