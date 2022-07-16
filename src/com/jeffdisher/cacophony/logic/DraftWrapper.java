package com.jeffdisher.cacophony.logic;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.jeffdisher.breakwater.utilities.Assert;
import com.jeffdisher.cacophony.data.local.v1.Draft;


/**
 * This is just a high-level wrapper over the directory where a single draft exists.
 */
public class DraftWrapper
{
	private static final String DRAFT_NAME = "draft.dat";
	private static final String ORIGINAL_VIDEO_NAME = "original_video.webm";
	private static final String PROCESSED_VIDEO_NAME = "processed_video.webm";
	private static final String THUMBNAIL_NAME = "thumbnail.jpeg";

	private final BasicDirectory _directory;

	public DraftWrapper(BasicDirectory directory)
	{
		_directory = directory;
	}

	public void saveDraft(Draft draft)
	{
		// This can be used for both new files and over-writing files.
		try (ObjectOutputStream stream = new ObjectOutputStream(_directory.writeFile(DRAFT_NAME)))
		{
			stream.writeObject(draft);
		}
		catch (IOException e)
		{
			// We have no reasonable way to handle this.
			throw Assert.unexpected(e);
		}
	}

	public Draft loadDraft()
	{
		// This can be used for both new files and over-writing files.
		try (ObjectInputStream stream = new ObjectInputStream(_directory.readFile(DRAFT_NAME)))
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

	public FileInputStream readOriginalVideo()
	{
		return _directory.readFile(ORIGINAL_VIDEO_NAME);
	}

	public FileOutputStream writeOriginalVideo()
	{
		return _directory.writeFile(ORIGINAL_VIDEO_NAME);
	}

	public FileInputStream readProcessedVideo()
	{
		return _directory.readFile(PROCESSED_VIDEO_NAME);
	}

	public FileOutputStream writeProcessedVideo()
	{
		return _directory.writeFile(PROCESSED_VIDEO_NAME);
	}

	public FileInputStream readThumbnail()
	{
		return _directory.readFile(THUMBNAIL_NAME);
	}

	public FileOutputStream writeThumbnail()
	{
		return _directory.writeFile(THUMBNAIL_NAME);
	}
}
