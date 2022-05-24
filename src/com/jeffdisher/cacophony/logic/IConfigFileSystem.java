package com.jeffdisher.cacophony.logic;

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
	 * Opens a config file for reading, if it exists.  The caller takes ownership of the stream.
	 * 
	 * @param fileName The name of the config file.
	 * @return The input stream for the file, null if the file didn't exist.
	 */
	InputStream readConfigFile(String fileName);

	/**
	 * Opens a config file for writing, overwriting any existing file.  The caller takes ownership of the stream.
	 * Assert fails if something goes wrong.
	 * 
	 * @param fileName The name of the config file.
	 * @return The output stream for the file.
	 */
	OutputStream writeConfigFile(String fileName);

	/**
	 * @return The config directory, only used for error reporting.
	 */
	String getDirectoryForReporting();
}
