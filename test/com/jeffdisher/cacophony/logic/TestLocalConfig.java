package com.jeffdisher.cacophony.logic;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.VersionException;


public class TestLocalConfig
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	@Test
	public void testCreation() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		LocalConfig config = LocalConfig.createNewConfig(fileSystem, new LocalDataModel(fileSystem), new MockFactory(true), "ipfs connect", "key name");
		Assert.assertNotNull(config);
	}

	@Test
	public void testLoad() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		LocalDataModel localDataModel = new LocalDataModel(fileSystem);
		MockFactory factory = new MockFactory(true);
		LocalConfig config = LocalConfig.createNewConfig(fileSystem, localDataModel, factory, "ipfs connect", "key name");
		Assert.assertNotNull(config);
		config = LocalConfig.loadExistingConfig(fileSystem, localDataModel, factory);
		Assert.assertNotNull(config);
	}

	@Test
	public void testLoadVersionProblem() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		LocalDataModel localDataModel = new LocalDataModel(fileSystem);
		LocalConfig config = LocalConfig.createNewConfig(fileSystem, localDataModel, new MockFactory(true), "ipfs connect", "key name");
		Assert.assertNotNull(config);
		try (OutputStream stream = fileSystem.writeConfigFile("version"))
		{
			stream.write(new byte[] { 2 });
		}
		try
		{
			config = LocalConfig.loadExistingConfig(fileSystem, localDataModel, null);
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
			LocalConfig config = LocalConfig.createNewConfig(fileSystem, new LocalDataModel(fileSystem), new MockFactory(false), "ipfs connect", "key name");
			Assert.assertNotNull(config);
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
			return new MockConnection();
		}
	}

	private static class MockConnection implements IConnection
	{
		@Override
		public List<Key> getKeys() throws IpfsConnectionException
		{
			Assert.fail("Not expected in test");
			return null;
		}
		@Override
		public IpfsFile storeData(InputStream dataStream) throws IpfsConnectionException
		{
			Assert.fail("Not expected in test");
			return null;
		}
		@Override
		public byte[] loadData(IpfsFile file) throws IpfsConnectionException
		{
			Assert.fail("Not expected in test");
			return null;
		}
		@Override
		public void publish(String keyName, IpfsFile file) throws IpfsConnectionException
		{
			Assert.fail("Not expected in test");
		}
		@Override
		public IpfsFile resolve(IpfsKey key) throws IpfsConnectionException
		{
			Assert.fail("Not expected in test");
			return null;
		}
		@Override
		public long getSizeInBytes(IpfsFile cid) throws IpfsConnectionException
		{
			Assert.fail("Not expected in test");
			return 0L;
		}
		@Override
		public URL urlForDirectFetch(IpfsFile cid)
		{
			Assert.fail("Not expected in test");
			return null;
		}
		@Override
		public void pin(IpfsFile cid) throws IpfsConnectionException
		{
			Assert.fail("Not expected in test");
		}
		@Override
		public void rm(IpfsFile cid) throws IpfsConnectionException
		{
			Assert.fail("Not expected in test");
		}
		@Override
		public Key generateKey(String keyName) throws IpfsConnectionException
		{
			Assert.fail("Not expected in test");
			return null;
		}
		@Override
		public void requestStorageGc() throws IpfsConnectionException
		{
			Assert.fail("Not expected in test");
		}
	}
}
