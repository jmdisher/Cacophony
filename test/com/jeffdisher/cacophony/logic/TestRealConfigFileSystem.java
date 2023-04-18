package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


public class TestRealConfigFileSystem
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	@Test
	public void trivialFile() throws Throwable
	{
		File directory = FOLDER.newFolder();
		RealConfigFileSystem fileSystem = new RealConfigFileSystem(directory);
		byte[] read = fileSystem.readTrivialFile("trivial");
		Assert.assertNull(read);
		byte[] wrote = new byte[] {1};
		fileSystem.writeTrivialFile("trivial", wrote);
		read = fileSystem.readTrivialFile("trivial");
		Assert.assertArrayEquals(wrote, read);
	}

	@Test
	public void configFile() throws Throwable
	{
		File directory = FOLDER.newFolder();
		RealConfigFileSystem fileSystem = new RealConfigFileSystem(directory);
		
		try (InputStream stream = fileSystem.readAtomicFile("config"))
		{
			Assert.assertNull(stream);
		}
		
		byte[] wrote = new byte[] {1, 2, 3};
		try (IConfigFileSystem.AtomicOutputStream output = fileSystem.writeAtomicFile("config"))
		{
			output.getStream().write(wrote);
			output.commit();
		}
		
		try (InputStream stream = fileSystem.readAtomicFile("config"))
		{
			byte[] read = stream.readAllBytes();
			Assert.assertArrayEquals(wrote, read);
		}
	}

	@Test
	public void writeAbort() throws Throwable
	{
		String fileName = "config";
		File directory = FOLDER.newFolder();
		RealConfigFileSystem fileSystem = new RealConfigFileSystem(directory);
		File finalFile = new File(directory, fileName);
		File tempFile = new File(directory, fileName + RealConfigFileSystem.TEMP_FILE_SUFFIX);
		
		byte[] wrote = new byte[] {1, 2, 3};
		try (IConfigFileSystem.AtomicOutputStream output = fileSystem.writeAtomicFile(fileName))
		{
			// Write but do NOT call commit - this should go nowhere.
			output.getStream().write(wrote);
		}
		try (InputStream stream = fileSystem.readAtomicFile(fileName))
		{
			Assert.assertNull(stream);
		}
		// Verify neither file exists.
		Assert.assertFalse(finalFile.exists());
		Assert.assertFalse(tempFile.exists());
		
		// Try writing data and then show the abort on change.
		try (IConfigFileSystem.AtomicOutputStream output = fileSystem.writeAtomicFile(fileName))
		{
			output.getStream().write(wrote);
			output.commit();
		}
		// Verify only final exists.
		Assert.assertTrue(finalFile.exists());
		Assert.assertFalse(tempFile.exists());
		try (IConfigFileSystem.AtomicOutputStream output = fileSystem.writeAtomicFile(fileName))
		{
			// Write but do NOT commit - we shouldn't change anything.
			output.getStream().write(new byte[] {4, 5, 6});
		}
		try (InputStream stream = fileSystem.readAtomicFile(fileName))
		{
			byte[] read = stream.readAllBytes();
			Assert.assertArrayEquals(wrote, read);
		}
		// Verify only final exists.
		Assert.assertTrue(finalFile.exists());
		Assert.assertFalse(tempFile.exists());
	}

	@Test
	public void brokenWriteBothFiles() throws Throwable
	{
		String fileName = "config";
		File directory = FOLDER.newFolder();
		RealConfigFileSystem fileSystem = new RealConfigFileSystem(directory);
		File finalFile = new File(directory, fileName);
		File tempFile = new File(directory, fileName + RealConfigFileSystem.TEMP_FILE_SUFFIX);
		
		// Create a case where both files exist and observe how reading fixes that.
		byte[] wroteTemp = new byte[] {1, 2, 3};
		byte[] wroteFinal = new byte[] {4, 5, 6, 7};
		Files.write(tempFile.toPath(), wroteTemp);
		Files.write(finalFile.toPath(), wroteFinal);
		
		try (InputStream stream = fileSystem.readAtomicFile(fileName))
		{
			byte[] read = stream.readAllBytes();
			// We should see the FINAL value since the TEMP will be considered a broken write.
			Assert.assertArrayEquals(wroteFinal, read);
		}
		// Verify that only final exists
		Assert.assertTrue(finalFile.exists());
		Assert.assertFalse(tempFile.exists());
	}

	@Test
	public void brokenWriteOnlyTemp() throws Throwable
	{
		String fileName = "config";
		File directory = FOLDER.newFolder();
		RealConfigFileSystem fileSystem = new RealConfigFileSystem(directory);
		File finalFile = new File(directory, fileName);
		File tempFile = new File(directory, fileName + RealConfigFileSystem.TEMP_FILE_SUFFIX);
		
		// Create a case where only TEMP exists and observe how reading fixes that.
		byte[] wroteTemp = new byte[] {1, 2, 3};
		Files.write(tempFile.toPath(), wroteTemp);
		
		try (InputStream stream = fileSystem.readAtomicFile(fileName))
		{
			byte[] read = stream.readAllBytes();
			// We should see the FINAL value since the TEMP will be considered a broken write.
			Assert.assertArrayEquals(wroteTemp, read);
		}
		// Verify that only final exists
		Assert.assertTrue(finalFile.exists());
		Assert.assertFalse(tempFile.exists());
	}
}
