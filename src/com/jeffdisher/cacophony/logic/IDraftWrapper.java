package com.jeffdisher.cacophony.logic;

import java.io.InputStream;
import java.io.OutputStream;

import com.jeffdisher.cacophony.data.local.v1.Draft;


/**
 * Just the interface for DraftWrapper which is exposed to other components (allowing DraftManager a more intimate
 * interface).
 */
public interface IDraftWrapper
{
	/**
	 * Saves the given draft data, overwriting whatever may have already been there.  This doesn't delete the videos or
	 * thumbnail.
	 * 
	 * @param draft The draft data to save to disk.
	 */
	void saveDraft(Draft draft);

	/**
	 * Loads the draft data from disk.
	 * Note that this will assert fail if the draft doesn't exist.
	 * 
	 * @return The deserialized Draft (never null).
	 */
	Draft loadDraft();

	/**
	 * Blocks until there are no thumbnail writers and then opens it for reading.  Doesn't change the draft data.
	 * 
	 * @return The stream to read the thumbnail (null if there is no thumbnail).
	 */
	InputStream readThumbnail();

	/**
	 * Blocks until there are no thumbnail readers or writers and then opens it for writing.  Doesn't change the draft
	 * data.
	 * 
	 * @return The stream to write the thumbnail.
	 */
	OutputStream writeThumbnail();

	/**
	 * Blocks until there are no thumbnail readers or writers and then deletes it from disk.  Doesn't change the draft
	 * data.
	 * 
	 * @return True if the thumbnail file existed and was deleted.
	 */
	boolean deleteThumbnail();

	/**
	 * Blocks until there are no original video writers and then opens it for reading.  Doesn't change the draft data.
	 * 
	 * @return The stream to read the original video (null if there is no original video).
	 */
	InputStream readOriginalVideo();

	/**
	 * Blocks until there are no original video readers or writers and then opens it for writing.  Doesn't change the
	 * draft data.
	 * 
	 * @return The stream to write the original video.
	 */
	OutputStream writeOriginalVideo();

	/**
	 * Blocks until there are no original video readers or writers and then deletes it from disk.  Doesn't change the
	 * draft data.
	 * 
	 * @return True if the original audio file existed and was deleted.
	 */
	boolean deleteOriginalVideo();

	/**
	 * Blocks until there are no processed video writers and then opens it for reading.  Doesn't change the draft data.
	 * 
	 * @return The stream to read the processed video (null if there is no processed video).
	 */
	InputStream readProcessedVideo();

	/**
	 * Blocks until there are no processed video readers or writers and then opens it for writing.  Doesn't change the
	 * draft data.
	 * 
	 * @return The stream to write the processed video.
	 */
	OutputStream writeProcessedVideo();

	/**
	 * Blocks until there are no processed video readers or writers and then deletes it from disk.  Doesn't change the
	 * draft data.
	 * 
	 * @return True if the processed video file existed and was deleted.
	 */
	boolean deleteProcessedVideo();

	/**
	 * Blocks until there are no audio writers and then opens it for reading.  Doesn't change the draft data.
	 * 
	 * @return The stream to read the audio (null if there is no audio).
	 */
	InputStream readAudio();

	/**
	 * Blocks until there are no audio readers or writers and then opens it for writing.  Doesn't change the draft data.
	 * 
	 * @return The stream to write the audio.
	 */
	OutputStream writeAudio();

	/**
	 * Blocks until there are no audio readers or writers and then deletes it from disk.  Doesn't change the draft data.
	 * 
	 * @return True if the audio file existed and was deleted.
	 */
	boolean deleteAudio();
}
