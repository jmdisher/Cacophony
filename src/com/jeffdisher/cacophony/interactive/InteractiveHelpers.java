package com.jeffdisher.cacophony.interactive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.core.CloseStatus;

import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.data.local.v1.SizedElement;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.DraftWrapper;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.PublishHelpers;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.Assert;

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
	public static VideoSaver openNewVideo(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		return new VideoSaver(draftManager, draftId);
	}
	public static void appendToNewVideo(VideoSaver saver, byte[] payload, int offset, int len)
	{
		saver.append(payload, offset, len);
	}
	public static void closeNewVideo(VideoSaver saver, String mime, int height, int width)
	{
		long savedFileSizeBytes = saver.sockedDidClose();
		
		// We now update the final state.
		Draft oldDraft = saver.draftWrapper.loadDraft();
		SizedElement originalVideo = new SizedElement(mime, height, width, savedFileSizeBytes);
		Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), originalVideo, oldDraft.processedVideo());
		saver.draftWrapper.saveDraft(newDraft);
	}

	// --- Methods related to processing the video (this is small since it mostly just invokes callbacks to the session on a different thread).
	public static VideoProcessor openVideoProcessor(VideoProcessor.ProcessWriter session, DraftManager draftManager, int draftId, String processCommand) throws FileNotFoundException, IOException
	{
		return new VideoProcessor(session, draftManager, draftId, processCommand);
	}
	public static void closeVideoProcessor(VideoProcessor processor)
	{
		processor.sockedDidClose();
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
	public static Draft readExistingDraft(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		return draftManager.openExistingDraft(draftId).loadDraft();
	}
	public static Draft updateDraftText(DraftManager draftManager, int draftId, String title, String description, String discussionUrl) throws FileNotFoundException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		Draft oldDraft = wrapper.loadDraft();
		Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), title, description, discussionUrl, oldDraft.thumbnail(), oldDraft.originalVideo(), oldDraft.processedVideo());
		wrapper.saveDraft(newDraft);
		return newDraft;
	}
	public static void deleteExistingDraft(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		draftManager.deleteExistingDraft(draftId);
	}
	public static void publishExistingDraft(IEnvironment environment
			, IReadWriteLocalData data
			, IConnection connection
			, INetworkScheduler scheduler
			, DraftManager draftManager
			, int draftId
	) throws FileNotFoundException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		Draft draft = wrapper.loadDraft();
		SizedElement video = draft.processedVideo();
		File videoFile = wrapper.processedVideo();
		if (null == video)
		{
			video = draft.originalVideo();
			videoFile = wrapper.originalVideo();
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
		PublishHelpers.PublishElement[] subElements = new PublishHelpers.PublishElement[elementCount];
		int index = 0;
		if (null != thumbnail)
		{
			subElements[index] = new PublishHelpers.PublishElement(thumbnail.mime(), new FileInputStream(wrapper.thumbnail()), thumbnail.height(), thumbnail.width(), true);
			index += 1;
		}
		if (null != video)
		{
			try
			{
				subElements[index] = new PublishHelpers.PublishElement(video.mime(), new FileInputStream(videoFile), video.height(), video.width(), false);
			}
			catch (FileNotFoundException e)
			{
				// Close the other file before we throw.
				closeElementFiles(environment, subElements);
				throw e;
			}
			index += 1;
		}
		
		LocalIndex localIndex = data.readLocalIndex();
		GlobalPinCache pinCache = data.readGlobalPinCache();
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler, connection);
		
		FuturePublish asyncPublish;
		try
		{
			asyncPublish = PublishHelpers.uploadFileAndStartPublish(environment, scheduler, connection, data, localIndex, pinCache, cache, draft.title(), draft.description(), draft.discussionUrl(), subElements);
		}
		catch (IpfsConnectionException e)
		{
			System.err.println("Publish command failed with IpfsConnectionException: " + e.getLocalizedMessage());
			e.printStackTrace();
			throw Assert.unexpected(e);
		}
		finally
		{
			closeElementFiles(environment, subElements);
		}
		
		// Save back other parts of the data store.
		data.writeGlobalPinCache(pinCache);
		
		// We can now wait for the publish to complete, now that we have closed all the local state.
		CommandHelpers.commonWaitForPublish(environment, asyncPublish);
	}

	// --- Methods related to thumbnails.
	public static void loadThumbnailToStream(DraftManager draftManager, int draftId, Consumer<String> mimeConsumer, OutputStream outStream) throws FileNotFoundException, IOException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		try (FileInputStream input = new FileInputStream(wrapper.thumbnail()))
		{
			mimeConsumer.accept(wrapper.loadDraft().thumbnail().mime());
			_copyToEndOfFile(input, outStream);
		}
	}
	public static void saveThumbnailFromStream(DraftManager draftManager, int draftId, int height, int width, String mime, InputStream inStream) throws FileNotFoundException, IOException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		long bytesCopied = 0L;
		try (FileOutputStream output = new FileOutputStream(wrapper.thumbnail()))
		{
			bytesCopied = _copyToEndOfFile(inStream, output);
		}
		Assert.assertTrue(bytesCopied > 0L);
		Draft oldDraft = wrapper.loadDraft();
		SizedElement thumbnail = new SizedElement(mime, height, width, bytesCopied);
		Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), thumbnail, oldDraft.originalVideo(), oldDraft.processedVideo());
		wrapper.saveDraft(newDraft);
	}

	// --- Methods related to video streaming.
	public static void writeOriginalVideoToStream(DraftManager draftManager, int draftId, BiConsumer<String, Long> mimeSizeConsumer, OutputStream outStream) throws FileNotFoundException, IOException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		File originalFile = wrapper.originalVideo();
		try (FileInputStream input = new FileInputStream(originalFile))
		{
			mimeSizeConsumer.accept(wrapper.loadDraft().originalVideo().mime(), originalFile.length());
			_copyToEndOfFile(input, outStream);
		}
	}
	public static void writeProcessedVideoToStream(DraftManager draftManager, int draftId, BiConsumer<String, Long> mimeSizeConsumer, OutputStream outStream) throws FileNotFoundException, IOException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		File processedFile = wrapper.processedVideo();
		try (FileInputStream input = new FileInputStream(processedFile))
		{
			mimeSizeConsumer.accept(wrapper.loadDraft().processedVideo().mime(), processedFile.length());
			_copyToEndOfFile(input, outStream);
		}
	}

	// --- Methods related to deleting videos.
	// Note that we don't build any special interlock around the delete relative to other operations like processing.
	// This is because that would be heavy-weight and complicated for something which we can prevent in the front-end
	//  and it would only corrupt the draft in progress.
	// More complex protection could be added in the future if this turns out to be a problem but this keeps it simple.
	/**
	 * Deletes the original video from the draft.
	 * @param draftManager The DraftManager.
	 * @param draftId The draft to open when searching for the video.
	 * @return True if this was deleted or false if it couldn't be or there was no video file.
	 * @throws FileNotFoundException The draft doesn't exist.
	 */
	public static boolean deleteOriginalVideo(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		File originalFile = wrapper.originalVideo();
		boolean didDelete = originalFile.delete();
		if (didDelete)
		{
			Draft oldDraft = wrapper.loadDraft();
			Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), null, oldDraft.processedVideo());
			wrapper.saveDraft(newDraft);
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
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		File processedFile = wrapper.processedVideo();
		boolean didDelete = processedFile.delete();
		if (didDelete)
		{
			Draft oldDraft = wrapper.loadDraft();
			Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), oldDraft.originalVideo(), null);
			wrapper.saveDraft(newDraft);
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
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		File originalFile = wrapper.thumbnail();
		boolean didDelete = originalFile.delete();
		if (didDelete)
		{
			Draft oldDraft = wrapper.loadDraft();
			Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), null, oldDraft.originalVideo(), oldDraft.processedVideo());
			wrapper.saveDraft(newDraft);
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
				session.close(CloseStatus.SERVER_ERROR, "Invalid XSRF");
			}
		}
		else
		{
			isSafe = false;
			System.err.println("Invalid IP: " + ip);
			session.close(CloseStatus.SERVER_ERROR, "Invalid IP");
		}
		return isSafe;
	}


	private static long _copyToEndOfFile(InputStream input, OutputStream output) throws IOException
	{
		long totalCopied = 0L;
		boolean reading = true;
		byte[] data = new byte[4096];
		while (reading)
		{
			int read = input.read(data);
			if (read > 0)
			{
				output.write(data, 0, read);
				totalCopied += (long)read;
			}
			else
			{
				reading = false;
			}
		}
		return totalCopied;
	}

	private static void closeElementFiles(IEnvironment environment, PublishHelpers.PublishElement[] elements)
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
