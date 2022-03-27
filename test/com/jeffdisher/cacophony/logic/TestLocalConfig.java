package com.jeffdisher.cacophony.logic;

import java.io.OutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.types.VersionException;


public class TestLocalConfig
{
	@Test
	public void testCreation() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		LocalConfig config = LocalConfig.createNewConfig(fileSystem, null, "ipfs connect", "key name");
		config.writeBackConfig();
	}

	@Test
	public void testLoad() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		LocalConfig config = LocalConfig.createNewConfig(fileSystem, null, "ipfs connect", "key name");
		config.writeBackConfig();
		config = LocalConfig.loadExistingConfig(fileSystem, null);
		config.writeBackConfig();
	}

	@Test
	public void testLoadVersionProblem() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		LocalConfig config = LocalConfig.createNewConfig(fileSystem, null, "ipfs connect", "key name");
		config.writeBackConfig();
		try (OutputStream stream = fileSystem.writeConfigFile("version"))
		{
			stream.write(new byte[] { 2 });
		}
		try
		{
			config = LocalConfig.loadExistingConfig(fileSystem, null);
			Assert.fail();
		}
		catch (VersionException e)
		{
			// expected.
		}
	}
}
