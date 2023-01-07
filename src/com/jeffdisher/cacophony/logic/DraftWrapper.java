package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This is just a high-level wrapper over the directory where a single draft exists.
 * Note that the wrapper doesn't force the Draft meta-data for the binary files to be in sync - that is up to the
 * caller.
 * Note that DraftWrapper instances are shared and long-lived, always looked up in the DraftManager.  This instance
 * sharing allows the DraftWrapper interface to use basic synchronization to make sure that concurrent accesses are
 * done safely.
 */
public class DraftWrapper
{
	// The serialized Draft object.
	private static final String DRAFT_NAME = "draft.dat";
	// The "original" video (we are currently just assuming webm but the name doesn't matter).
	private static final String ORIGINAL_VIDEO_NAME = "original_video.webm";
	// The "processed" video (we are currently just assuming webm but the name doesn't matter).
	private static final String PROCESSED_VIDEO_NAME = "processed_video.webm";
	// The audio (we are currently just assuming ogg but the name doesn't matter).
	private static final String AUDIO_NAME = "audio.ogg";
	// The thumbnail image (we are currently just assuming JPEG but the name doesn't matter).
	private static final String THUMBNAIL_NAME = "thumbnail.jpeg";

	private final File _directory;

	// The structures we use for gating the read/write access.
	private final ReferenceTuple _thumbTracking;
	private final ReferenceTuple _originalTracking;
	private final ReferenceTuple _processedTracking;
	private final ReferenceTuple _audioTracking;

	/**
	 * Creates a DraftWrapper on top of the given directory.  Note that this should only be containing the draft data.
	 * 
	 * @param directory The directory to use as the backing-store for the draft data.
	 */
	public DraftWrapper(File directory)
	{
		Assert.assertTrue(directory.isDirectory());
		_directory = directory;
		_thumbTracking = new ReferenceTuple(THUMBNAIL_NAME);
		_originalTracking = new ReferenceTuple(ORIGINAL_VIDEO_NAME);
		_processedTracking = new ReferenceTuple(PROCESSED_VIDEO_NAME);
		_audioTracking = new ReferenceTuple(AUDIO_NAME);
	}

	/**
	 * Saves the given draft data, overwriting whatever may have already been there.  This doesn't delete the videos or
	 * thumbnail.
	 * 
	 * @param draft The draft data to save to disk.
	 */
	public synchronized void saveDraft(Draft draft)
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
	public synchronized Draft loadDraft()
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
	 * Attempts to delete the draft.
	 * WARNING:  THIS CAN ONLY BE CALLED BY THE DRAFTMANAGER!
	 * TODO:  Fix how these are returned so that callers only see a minimal interface.
	 * 
	 * @return True if the draft was deleted and false if the delete couldn't be performed due to open readers or
	 * writers.
	 */
	public synchronized boolean deleteDraft()
	{
		boolean didDelete = false;
		if ((0 == _thumbTracking.readerCount)
				&& (0 == _originalTracking.readerCount)
				&& (0 == _processedTracking.readerCount)
				&& (0 == _audioTracking.readerCount)
				&& (null == _thumbTracking.writer)
				&& (null == _originalTracking.writer)
				&& (null == _processedTracking.writer)
				&& (null == _audioTracking.writer)
		)
		{
			try
			{
				Files.walk(_directory.toPath())
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
			}
			catch (IOException e)
			{
				// We don't expect a file system access error here.
				throw Assert.unexpected(e);
			}
			didDelete = true;
		}
		return didDelete;
	}

	/**
	 * Blocks until there are no thumbnail writers and then opens it for reading.  Doesn't change the draft data.
	 * 
	 * @return The stream to read the thumbnail (null if there is no thumbnail).
	 */
	public synchronized InputStream readThumbnail()
	{
		return _readCase(_thumbTracking);
	}

	/**
	 * Blocks until there are no thumbnail readers or writers and then opens it for writing.  Doesn't change the draft
	 * data.
	 * 
	 * @return The stream to write the thumbnail.
	 */
	public synchronized OutputStream writeThumbnail()
	{
		return _writeCase(_thumbTracking);
	}

	/**
	 * Blocks until there are no thumbnail readers or writers and then deletes it from disk.  Doesn't change the draft
	 * data.
	 * 
	 * @return True if the thumbnail file existed and was deleted.
	 */
	public synchronized boolean deleteThumbnail()
	{
		return _deleteCase(_thumbTracking);
	}

	/**
	 * Blocks until there are no original video writers and then opens it for reading.  Doesn't change the draft data.
	 * 
	 * @return The stream to read the original video (null if there is no original video).
	 */
	public synchronized InputStream readOriginalVideo()
	{
		return _readCase(_originalTracking);
	}

	/**
	 * Blocks until there are no original video readers or writers and then opens it for writing.  Doesn't change the
	 * draft data.
	 * 
	 * @return The stream to write the original video.
	 */
	public synchronized OutputStream writeOriginalVideo()
	{
		return _writeCase(_originalTracking);
	}

	/**
	 * Blocks until there are no original video readers or writers and then deletes it from disk.  Doesn't change the
	 * draft data.
	 * 
	 * @return True if the original audio file existed and was deleted.
	 */
	public synchronized boolean deleteOriginalVideo()
	{
		return _deleteCase(_originalTracking);
	}

	/**
	 * Blocks until there are no processed video writers and then opens it for reading.  Doesn't change the draft data.
	 * 
	 * @return The stream to read the processed video (null if there is no processed video).
	 */
	public synchronized InputStream readProcessedVideo()
	{
		return _readCase(_processedTracking);
	}

	/**
	 * Blocks until there are no processed video readers or writers and then opens it for writing.  Doesn't change the
	 * draft data.
	 * 
	 * @return The stream to write the processed video.
	 */
	public synchronized OutputStream writeProcessedVideo()
	{
		return _writeCase(_processedTracking);
	}

	/**
	 * Blocks until there are no processed video readers or writers and then deletes it from disk.  Doesn't change the
	 * draft data.
	 * 
	 * @return True if the processed video file existed and was deleted.
	 */
	public synchronized boolean deleteProcessedVideo()
	{
		return _deleteCase(_processedTracking);
	}

	/**
	 * Blocks until there are no audio writers and then opens it for reading.  Doesn't change the draft data.
	 * 
	 * @return The stream to read the audio (null if there is no audio).
	 */
	public synchronized InputStream readAudio()
	{
		return _readCase(_audioTracking);
	}

	/**
	 * Blocks until there are no audio readers or writers and then opens it for writing.  Doesn't change the draft data.
	 * 
	 * @return The stream to write the audio.
	 */
	public synchronized OutputStream writeAudio()
	{
		return _writeCase(_audioTracking);
	}

	/**
	 * Blocks until there are no audio readers or writers and then deletes it from disk.  Doesn't change the draft data.
	 * 
	 * @return True if the audio file existed and was deleted.
	 */
	public synchronized boolean deleteAudio()
	{
		return _deleteCase(_audioTracking);
	}


	private File _draftFile()
	{
		return new File(_directory, DRAFT_NAME);
	}

	private InputStream _readCase(ReferenceTuple tracking)
	{
		while (null != tracking.writer)
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// We can't call this path on an interruptable thread.
				throw Assert.unexpected(e);
			}
		}
		InputStream stream = null;
		File file = new File(_directory, tracking.fileName);
		// We want to return null on missing file, not throw, since some cases use function objects to access this.
		if (file.exists())
		{
			try
			{
				stream = new ClosingInputStream(new FileInputStream(file), () -> tracking.readerCount -= 1);
				tracking.readerCount += 1;
			}
			catch (FileNotFoundException e)
			{
				// This case can't happen.
				throw Assert.unexpected(e);
			}
		}
		return stream;
	}

	private OutputStream _writeCase(ReferenceTuple tracking)
	{
		while ((null != tracking.writer) || (0 != tracking.readerCount))
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// We can't call this path on an interruptable thread.
				throw Assert.unexpected(e);
			}
		}
		OutputStream stream = null;
		File file = new File(_directory, tracking.fileName);
		try
		{
			stream = new ClosingOutputStream(new FileOutputStream(file), () -> tracking.writer = null);
			tracking.writer = stream;
		}
		catch (FileNotFoundException e)
		{
			// This case can't happen.
			throw Assert.unexpected(e);
		}
		return stream;
	}

	private boolean _deleteCase(ReferenceTuple tracking)
	{
		while ((null != tracking.writer) || (0 != tracking.readerCount))
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// We can't call this path on an interruptable thread.
				throw Assert.unexpected(e);
			}
		}
		File file = new File(_directory, tracking.fileName);
		boolean didExist = true;
		if (file.exists())
		{
			boolean didDelete = file.delete();
			// We don't know why this would fail.
			Assert.assertTrue(didDelete);
			// We just want to say this was a success if we tried.
			didExist = true;
		}
		return didExist;
	}

	private synchronized void _internal_processNotifyHandler(Runnable handler)
	{
		// This function is called by our stream sub-types when closing since they all want to change a state under
		// monitor and then notify anyone waiting.
		handler.run();
		this.notifyAll();
	}


	private class ClosingOutputStream extends OutputStream
	{
		private final OutputStream _stream;
		private final Runnable _closeHandler;
		public ClosingOutputStream(OutputStream stream, Runnable closeHandler)
		{
			_stream = stream;
			_closeHandler = closeHandler;
		}
		@Override
		public void close() throws IOException
		{
			_internal_processNotifyHandler(_closeHandler);
			_stream.close();
		}
		@Override
		public void write(int arg0) throws IOException
		{
			_stream.write(arg0);
		}
		@Override
		public void write(byte[] arg0, int arg1, int arg2) throws IOException
		{
			_stream.write(arg0, arg1, arg2);
		}
	}

	private class ClosingInputStream extends InputStream
	{
		private final InputStream _stream;
		private final Runnable _closeHandler;
		public ClosingInputStream(InputStream stream, Runnable closeHandler)
		{
			_stream = stream;
			_closeHandler = closeHandler;
		}
		@Override
		public void close() throws IOException
		{
			_internal_processNotifyHandler(_closeHandler);
			_stream.close();
		}
		@Override
		public int read() throws IOException
		{
			return _stream.read();
		}
		@Override
		public int read(byte[] b, int off, int len) throws IOException
		{
			return _stream.read(b, off, len);
		}
	}

	private static class ReferenceTuple
	{
		// This is just a mutable container.
		public final String fileName;
		public int readerCount;
		public OutputStream writer;
		public ReferenceTuple(String fileName)
		{
			this.fileName = fileName;
		}
	}
}
