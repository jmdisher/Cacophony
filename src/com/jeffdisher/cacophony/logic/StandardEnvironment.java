package com.jeffdisher.cacophony.logic;

import java.io.File;

import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.utils.Assert;


public class StandardEnvironment implements IEnvironment
{
	private final LocalDataModel _sharedDataModel;
	private final IConnection _connection;
	private final INetworkScheduler _scheduler;
	private final DraftManager _sharedDraftManager;

	public StandardEnvironment(File topLevelDraftsDirectory
			, LocalDataModel sharedDataModel
			, IConnection connection
			, INetworkScheduler scheduler
	)
	{
		Assert.assertTrue(null != topLevelDraftsDirectory);
		
		_sharedDataModel = sharedDataModel;
		_connection = connection;
		_scheduler = scheduler;
		_sharedDraftManager = new DraftManager(topLevelDraftsDirectory);
	}

	@Override
	public INetworkScheduler getSharedScheduler()
	{
		Assert.assertTrue(null != _scheduler);
		return _scheduler;
	}

	/**
	 * Builds a new DraftManager instance on top of the config's filesystem's draft directory.
	 * 
	 * @return The new DraftManager instance.
	 */
	@Override
	public DraftManager getSharedDraftManager()
	{
		return _sharedDraftManager;
	}

	@Override
	public LocalDataModel getSharedDataModel()
	{
		return _sharedDataModel;
	}

	@Override
	public IConnection getConnection()
	{
		return _connection;
	}

	@Override
	public long currentTimeMillis()
	{
		return System.currentTimeMillis();
	}
}
