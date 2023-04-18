package com.jeffdisher.cacophony.logic;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * An abstract interface over the local filesystem for reading/writing our local data, related to configuration.
 * This isn't generally useful outside of the config directory.
 * The entire reason why this exists is to allow test coverage on common serialization/deserialization without requiring
 * a real disk.
 */
public interface IConfigFileSystem
{
	/**
	 * Attempts to create the config directory.
	 * 
	 * @return True if it was created or was at least empty, false if it already contained data or couldn't be created.
	 */
	boolean createConfigDirectory();

	/**
	 * Used to check if the config directory exists.  Does not check anything else about the contents of the directory.
	 * 
	 * @return If the config directory exists.
	 */
	boolean doesConfigDirectoryExist();

	/**
	 * Reads and returns all the data in the file called fileName.
	 * 
	 * @param fileName The name of the file to read.
	 * @return The contents of the file, null if it couldn't be found.
	 */
	byte[] readTrivialFile(String fileName);

	/**
	 * Writes the given data to the file called fileName.
	 * 
	 * @param fileName The name of the file to write.
	 * @param data The data to write to the file (cannot be null).
	 */
	void writeTrivialFile(String fileName, byte[] data);

	/**
	 * Opens a file for reading, if it exists.  The caller takes ownership of the stream.
	 * This assumes that the file was written using our portable atomic pattern, so it will not open an incomplete file.
	 * 
	 * @param fileName The name of the file.
	 * @return The input stream for the file, null if the file didn't exist.
	 */
	InputStream readAtomicFile(String fileName);

	/**
	 * Opens a file for writing using our portable atomic pattern.  The caller takes ownership of the stream.
	 * The caller MUST call commit() on the returned object before close if it wants the write to become durable.
	 * 
	 * @param fileName The name of the file.
	 * @return The atomic output abstraction to use in writing the file.
	 */
	AtomicOutputStream writeAtomicFile(String fileName);

	/**
	 * This is a pretty low-level call since the drafts can meaningful use a storage abstraction as they are just a few
	 * large files.  The DraftManager is responsible for owning and managing the contents of this directory.
	 * This directory will exist as a directory on-disk or IOException will be thrown.
	 * 
	 * @return The directory where drafts should be stored.
	 * @throws IOException The directory doesn't exist and couldn't be created.
	 */
	File getDraftsTopLevelDirectory() throws IOException;


	/**
	 * We do file IO using the typical atomic write trick:  Write to a temp file and then rename it to replace the
	 * original file.  This interface allows a way to only do the final rename if the output was explicitly told to
	 * commit before being closed (then, it will perform the atomic when closing).
	 * NOTE:  I have previously had problems with this not being supported on Windows systems so this approach will NOT
	 * use the explicit atomic rename but will do the delete and rename, manually, as a 2-step process.  The consequence
	 * of this is that the read must also apply some special logic so it can clean up a broken write, if it observes
	 * one.
	 */
	public interface AtomicOutputStream extends Closeable
	{
		OutputStream getStream();
		void commit();
	}
}
