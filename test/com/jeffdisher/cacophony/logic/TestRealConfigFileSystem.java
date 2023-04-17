package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

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
		
		try (InputStream stream = fileSystem.readConfigFile("config"))
		{
			Assert.assertNull(stream);
		}
		
		byte[] wrote = new byte[] {1, 2, 3};
		try (OutputStream stream = fileSystem.writeConfigFile("config"))
		{
			stream.write(wrote);
		}
		
		try (InputStream stream = fileSystem.readConfigFile("config"))
		{
			byte[] read = stream.readAllBytes();
			Assert.assertArrayEquals(wrote, read);
		}
	}
}
