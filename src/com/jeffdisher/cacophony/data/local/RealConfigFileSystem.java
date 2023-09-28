package com.jeffdisher.cacophony.data.local;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import com.jeffdisher.cacophony.utils.Assert;


public class RealConfigFileSystem implements IConfigFileSystem
{
	public static final String DRAFTS_DIRECTORY_NAME = "drafts";
	public static final String TEMP_FILE_SUFFIX = ".temp";

	private final File _directory;

	public RealConfigFileSystem(File directory)
	{
		_directory = directory;
	}

	@Override
	public boolean createConfigDirectory()
	{
		// If the directory doesn't exist, or does exist and is empty, we consider this a "success".
		_directory.mkdirs();
		return (0 == _directory.list().length);
	}

	@Override
	public boolean doesConfigDirectoryExist()
	{
		return _directory.isDirectory();
	}

	@Override
	public byte[] readTrivialFile(String fileName)
	{
		File file = new File(_directory, fileName);
		try
		{
			return file.exists()
					? Files.readAllBytes(file.toPath())
					: null
			;
		}
		catch (IOException e)
		{
			// We don't expect errors on local file access after we checked it existed.
			throw Assert.unexpected(e);
		}
	}

	@Override
	public void writeTrivialFile(String fileName, byte[] data)
	{
		File file = new File(_directory, fileName);
		try
		{
			Files.write(file.toPath(), data);
		}
		catch (IOException e)
		{
			// We don't expect errors on local file access.
			throw Assert.unexpected(e);
		}
	}

	@Override
	public InputStream readAtomicFile(String fileName)
	{
		// Since the atomic rename may not work on Windows systems, our manual approach requires potential clean-up on the read side.
		File finalFile = new File(_directory, fileName);
		File tempFile = new File(_directory, fileName + TEMP_FILE_SUFFIX);
		
		// Broken write clean-up logic:
		// -if only FINAL, change nothing and open it (means the write was clean)
		// -if only TEMP, rename it to final and open it (means the write finished but the rename was interrupted)
		// -if both, delete TEMP and open FINAL (means we don't know the state of the write so we discard it)
		// -if neither exist, return null
		
		if (finalFile.exists())
		{
			if (tempFile.exists())
			{
				boolean didDelete = tempFile.delete();
				// This shouldn't fail if it exists, aside from a race or permissions issue.
				Assert.assertTrue(didDelete);
			}
		}
		else if (tempFile.exists())
		{
			boolean didRename = tempFile.renameTo(finalFile);
			// This shouldn't fail if only the one exists, aside from a race or permissions issue.
			Assert.assertTrue(didRename);
		}
		
		FileInputStream stream = null;
		try
		{
			stream = new FileInputStream(finalFile);
		}
		catch (FileNotFoundException e)
		{
			// In this case, we just return null.
			stream = null;
		}
		return stream;
	}

	@Override
	public IConfigFileSystem.AtomicOutputStream writeAtomicFile(String fileName)
	{
		// We need to manually do this atomic write, since Windows historically had issues with this.
		File finalFile = new File(_directory, fileName);
		File tempFile = new File(_directory, fileName + TEMP_FILE_SUFFIX);
		
		// The steps:
		// 1) write to TEMP
		// 2) delete FINAL (it may not exist if this is the first write - the first write is NOT atomic since we could see this as a broken file)
		// 3) rename TEMP to FINAL
		
		// We can't already see a partial write (the read or previous write should have fixed that).
		Assert.assertTrue(!tempFile.exists());
		
		FileOutputStream output;
		try
		{
			output = new FileOutputStream(tempFile);
		}
		catch (FileNotFoundException e)
		{
			// We don't expect this here since we already verified the directory exists.
			throw Assert.unexpected(e);
		}
		
		return new IConfigFileSystem.AtomicOutputStream()
		{
			private boolean _didCommit = false;
			@Override
			public void close() throws IOException
			{
				output.close();
				if (_didCommit)
				{
					// Do the dance to delete the old and rename the new.
					// (may not delete if this didn't exist)
					if (finalFile.exists())
					{
						boolean didDelete = finalFile.delete();
						// We depend on being able to delete this and should only fail due to a race or permissions.
						Assert.assertTrue(didDelete);
					}
					boolean didRename = tempFile.renameTo(finalFile);
					// This shouldn't fail if only the one exists, aside from a race or permissions issue.
					Assert.assertTrue(didRename);
				}
				else
				{
					System.err.println("WARNING:  Abandoning write to " + fileName);
					// There was a problem so just delete the new.
					boolean didDelete = tempFile.delete();
					// We created this so we shouldn't fail to delete it.
					Assert.assertTrue(didDelete);
				}
			}
			@Override
			public OutputStream getStream()
			{
				return output;
			}
			@Override
			public void commit()
			{
				_didCommit = true;
			}
		};
	}

	@Override
	public File getDraftsTopLevelDirectory() throws IOException
	{
		File draftsDirectory = new File(_directory, DRAFTS_DIRECTORY_NAME);
		// Make sure that the directory exists.
		if (!draftsDirectory.isDirectory())
		{
			boolean didMake = draftsDirectory.mkdirs();
			if (!didMake)
			{
				throw new IOException("Failed to create directory: " + draftsDirectory);
			}
		}
		// We are going to assume that the wrong file type is just a corrupt config.
		Assert.assertTrue(draftsDirectory.isDirectory());
		return draftsDirectory;
	}
}
