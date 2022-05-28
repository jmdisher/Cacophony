package com.jeffdisher.cacophony.logic;

import java.io.OutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.VersionException;


public class TestLocalConfig
{
	@Test
	public void testCreation() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		LocalConfig config = LocalConfig.createNewConfig(fileSystem, new MockFactory(true), "ipfs connect", "key name");
		config.writeBackConfig();
	}

	@Test
	public void testLoad() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		LocalConfig config = LocalConfig.createNewConfig(fileSystem, new MockFactory(true), "ipfs connect", "key name");
		config.writeBackConfig();
		config = LocalConfig.loadExistingConfig(fileSystem, null);
		config.writeBackConfig();
	}

	@Test
	public void testLoadVersionProblem() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		LocalConfig config = LocalConfig.createNewConfig(fileSystem, new MockFactory(true), "ipfs connect", "key name");
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

	@Test
	public void testFailedConnection() throws Throwable
	{
		boolean didFail = false;
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		try {
			LocalConfig config = LocalConfig.createNewConfig(fileSystem, new MockFactory(false), "ipfs connect", "key name");
			config.writeBackConfig();
		}
		catch (IpfsConnectionException e)
		{
			didFail = true;
		}
		Assert.assertFalse(fileSystem.doesConfigDirectoryExist());
		Assert.assertTrue(didFail);
	}


	private static class MockFactory implements IConnectionFactory
	{
		private boolean _allow;
		private MockFactory(boolean allow)
		{
			_allow = allow;
		}
		@Override
		public IConnection buildConnection(String ipfsHost) throws IpfsConnectionException
		{
			if (!_allow)
			{
				throw new IpfsConnectionException(null, null, null);
			}
			return null;
		}
	}
}
