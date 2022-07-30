package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This is just a high-level wrapper over the directory where a single draft exists.
 * Note that the wrapper doesn't force the Draft meta-data for the binary files to be in sync - that is up to the
 * caller.
 */
public class DraftWrapper
{
	// The serialized Draft object.
	private static final String DRAFT_NAME = "draft.dat";
	// The "original" video (we are currently just assuming webm but the name doesn't matter).
	private static final String ORIGINAL_VIDEO_NAME = "original_video.webm";
	// The "processed" video (we are currently just assuming webm but the name doesn't matter).
	private static final String PROCESSED_VIDEO_NAME = "processed_video.webm";
	// The thumbnail image (we are currently just assuming JPEG but the name doesn't matter).
	private static final String THUMBNAIL_NAME = "thumbnail.jpeg";

	private final File _directory;

	/**
	 * Creates a DraftWrapper on top of the given directory.  Note that this should only be containing the draft data.
	 * 
	 * @param directory The directory to use as the backing-store for the draft data.
	 */
	public DraftWrapper(File directory)
	{
		Assert.assertTrue(directory.isDirectory());
		_directory = directory;
	}

	/**
	 * Saves the given draft data, overwriting whatever may have already been there.  This doesn't delete the videos or
	 * thumbnail.
	 * 
	 * @param draft The draft data to save to disk.
	 */
	public void saveDraft(Draft draft)
	{
		// This can be used for both new files and over-writing files.
		try (ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(_draftFile())))
		{
			stream.writeObject(draft);
		}
		catch (IOException e)
		{
			// We have no reasonable way to handle this.
			throw Assert.unexpected(e);
		}
	}

	/**
	 * Loads the draft data from disk.
	 * Note that this will assert fail if the draft doesn't exist.
	 * 
	 * @return The deserialized Draft (never null).
	 */
	public Draft loadDraft()
	{
		// This can be used for both new files and over-writing files.
		try (ObjectInputStream stream = new ObjectInputStream(new FileInputStream(_draftFile())))
		{
			return (Draft) stream.readObject();
		}
		catch (IOException e)
		{
			// We have no reasonable way to handle this.
			throw Assert.unexpected(e);
		}
		catch (ClassNotFoundException e)
		{
			// This would be corrupt data or a broken installation.
			throw Assert.unexpected(e);
		}
	}

	/**
	 * @return The thumbnail image file.
	 */
	public File thumbnail()
	{
		return new File(_directory, THUMBNAIL_NAME);
	}

	/**
	 * @return The original video file.
	 */
	public File originalVideo()
	{
		return new File(_directory, ORIGINAL_VIDEO_NAME);
	}

	/**
	 * @return The processed video file.
	 */
	public File processedVideo()
	{
		return new File(_directory, PROCESSED_VIDEO_NAME);
	}


	private File _draftFile()
	{
		return new File(_directory, DRAFT_NAME);
	}
}
