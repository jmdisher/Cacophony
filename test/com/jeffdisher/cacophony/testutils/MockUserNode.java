package com.jeffdisher.cacophony.testutils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;

import com.jeffdisher.cacophony.DataDomain;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.caches.EntryCacheRegistry;
import com.jeffdisher.cacophony.caches.HomeUserReplyCache;
import com.jeffdisher.cacophony.caches.LocalRecordCache;
import com.jeffdisher.cacophony.caches.LocalUserInfoCache;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.StandardLogger;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This is just the combination of all the elements normally associated with a single node and single channel/user.
 */
public class MockUserNode
{
	private final String _localKeyName;
	private final MockSingleNode _sharedConnection;
	private final MemoryConfigFileSystem _fileSystem;
	private final SilentLogger _logger;

	// We lazily create the executor so that it can be shut down to drop data caches and force the scheduler reset.
	private MultiThreadedScheduler _lazyScheduler;
	private Context _lazyContext;

	public MockUserNode(String keyName, IpfsKey key, MockSingleNode node, File draftsDir)
	{
		_localKeyName = keyName;
		_sharedConnection = node;
		_sharedConnection.addNewKey(keyName, key);
		_fileSystem = new MemoryConfigFileSystem(draftsDir);
		_logger = new SilentLogger();
	}

	public void setContextCaches(LocalRecordCache recordCache, LocalUserInfoCache userInfoCache, EntryCacheRegistry entryRegistry, HomeUserReplyCache replyCache)
	{
		// This will force the context to be created.
		Assert.assertTrue(null == _lazyContext);
		Assert.assertTrue(null == _lazyScheduler);
		_lazyScheduler = new MultiThreadedScheduler(_sharedConnection, 1);
		LocalDataModel model;
		try
		{
			model = LocalDataModel.verifiedAndLoadedModel(LocalDataModel.NONE, _fileSystem, _lazyScheduler);
		}
		catch (UsageException e)
		{
			// We don't expect this in the test.
			throw Assert.unexpected(e);
		}
		_lazyContext = new Context(new DraftManager(_fileSystem.getDraftsTopLevelDirectory())
				, model
				, _sharedConnection
				, _lazyScheduler
				, () -> System.currentTimeMillis()
				, _logger
				, DataDomain.FAKE_BASE_URL
				, recordCache
				, userInfoCache
				, entryRegistry
				, replyCache
				, null
		);
	}

	public void createChannel(String keyName, String name, String description, byte[] userPicData) throws Throwable
	{
		ByteArrayInputStream pictureStream = new ByteArrayInputStream(userPicData);
		
		CreateChannelCommand createChannel = new CreateChannelCommand(keyName);
		ICommand.Result result = createChannel.runInContext(_lazyContext());
		_handleResult(result);
		UpdateDescriptionCommand updateDescription = new UpdateDescriptionCommand(name, description, pictureStream, null, null, null);
		result = updateDescription.runInContext(_lazyContext());
		_handleResult(result);
	}

	/**
	 * Runs the given command on this mock node.
	 * 
	 * @param captureStream The stream to use to override the logging output (can be null).
	 * @param command The command to run.
	 * @return The result object on success, null if we detected a partial failure (exception on true failure).
	 * @throws Throwable Something went wrong which wasn't just a simple failed command.
	 */
	public <T extends ICommand.Result> T runCommand(OutputStream captureStream, ICommand<T> command) throws Throwable
	{
		ILogger logger = _logger;
		Context defaultContext = _lazyContext();
		Context usedContext = defaultContext;
		// See if we want to override the output capture.
		if (null != captureStream)
		{
			logger = StandardLogger.topLogger(new PrintStream(captureStream), false);
			IpfsKey publicKey = defaultContext.getSelectedKey();
			usedContext = new Context(defaultContext.sharedDraftManager
					, defaultContext.sharedDataModel
					, defaultContext.basicConnection
					, defaultContext.scheduler
					, defaultContext.currentTimeMillisGenerator
					, logger
					, defaultContext.baseUrl
					, null
					, null
					, null
					, null
					, publicKey
			);
		}
		T result = command.runInContext(usedContext);
		if (usedContext != defaultContext)
		{
			IpfsKey newKey = usedContext.getSelectedKey();
			Assert.assertTrue(defaultContext.getSelectedKey().equals(newKey));
			defaultContext.setSelectedKey(newKey);
		}
		_handleResult(result);
		return logger.didErrorOccur()
				? null
				: result
		;
	}

	public byte[] loadDataFromNode(IpfsFile cid) throws IpfsConnectionException
	{
		return _sharedConnection.loadDataFromNode(cid);
	}

	public IpfsFile storeDataToNode(byte[] recordData) throws IpfsConnectionException
	{
		return _sharedConnection.storeData(new ByteArrayInputStream(recordData));
	}

	public IpfsFile resolveKeyOnNode(IpfsKey key) throws IpfsConnectionException
	{
		return _sharedConnection.resolve(key);
	}

	public IpfsFile getLastRootElement() throws IpfsConnectionException
	{
		try (IReadingAccess reading = StandardAccess.readAccess(_lazyContext()))
		{
			return reading.getLastRootElement();
		}
	}

	public boolean isPinnedLocally(IpfsFile file)
	{
		return _sharedConnection.isPinned(file);
	}

	public boolean isInPinCache(IpfsFile file) throws IpfsConnectionException
	{
		try (IReadingAccess reading = StandardAccess.readAccess(_lazyContext()))
		{
			return reading.isInPinCached(file);
		}
	}

	public PrefsData readPrefs() throws IpfsConnectionException
	{
		try (IReadingAccess reading = StandardAccess.readAccess(_lazyContext()))
		{
			return reading.readPrefs();
		}
	}

	public IFolloweeReading readFollowIndex() throws IpfsConnectionException
	{
		// We use the write accessor since we want the full FollowIndex interface for tests (returning this outside of the access closure is incorrect, either way).
		try (IWritingAccess writing = StandardAccess.writeAccess(_lazyContext()))
		{
			return writing.readableFolloweeData();
		}
	}

	public void deleteFile(IpfsFile cid)
	{
		try
		{
			_sharedConnection.rm(cid);
		}
		catch (IpfsConnectionException e)
		{
			throw Assert.unexpected(e);
		}
	}

	public void shutdown()
	{
		Assert.assertTrue(null != _lazyScheduler);
		Assert.assertTrue(null != _lazyContext);
		_lazyScheduler.shutdown();
		_lazyScheduler = null;
		_lazyContext = null;
	}

	public void timeoutKey(IpfsKey publicKey)
	{
		_sharedConnection.timeoutKey(publicKey);
	}

	public void assertConsistentPinCache() throws UsageException
	{
		LocalDataModel.verifiedAndLoadedModel(LocalDataModel.NONE, _fileSystem, _lazyScheduler);
	}

	public void manualPublishLocal(IpfsFile manualRoot) throws IpfsConnectionException
	{
		_sharedConnection.publish(_localKeyName, null, manualRoot);
	}

	public IConfigFileSystem getFileSystem()
	{
		return _fileSystem;
	}

	public INetworkScheduler getScheduler()
	{
		return _lazyScheduler;
	}

	public Context getContext()
	{
		return _lazyContext();
	}


	private void _handleResult(ICommand.Result result) throws IpfsConnectionException
	{
		IpfsFile rootToPublish = result.getIndexToPublish();
		if (null != rootToPublish)
		{
			_sharedConnection.publish(_localKeyName, null, rootToPublish);
		}
	}

	private Context _lazyContext()
	{
		if (null == _lazyContext)
		{
			Assert.assertTrue(null == _lazyScheduler);
			_lazyScheduler = new MultiThreadedScheduler(_sharedConnection, 1);
			LocalDataModel model;
			try
			{
				model = LocalDataModel.verifiedAndLoadedModel(LocalDataModel.NONE, _fileSystem, _lazyScheduler);
			}
			catch (UsageException e)
			{
				// We don't expect this in the test.
				throw Assert.unexpected(e);
			}
			_lazyContext = new Context(new DraftManager(_fileSystem.getDraftsTopLevelDirectory())
					, model
					, _sharedConnection
					, _lazyScheduler
					, () -> System.currentTimeMillis()
					, _logger
					, DataDomain.FAKE_BASE_URL
					, null
					, null
					, null
					, null
					, null
			);
		}
		return _lazyContext;
	}
}
