package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.OutputStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.VersionException;


public class TestLocalConfig
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

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

	@Test
	public void testDraftManager() throws Throwable
	{
		RealConfigFileSystem fileSystem = new RealConfigFileSystem(new File(FOLDER.newFolder(), "config"));
		LocalConfig config = LocalConfig.createNewConfig(fileSystem, new MockFactory(true), "ipfs connect", "key name");
		config.writeBackConfig();
		DraftManager draftManager = config.buildDraftManager();
		DraftWrapper wrapper = draftManager.createNewDraft(1);
		Draft draft = wrapper.loadDraft();
		Assert.assertEquals(1, draft.id());
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
