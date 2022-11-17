package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.breakwater.IPostRawHandler;
import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Publishes the given draft and returns 200 on success, 404 if not found, or 500 if something went wrong.
 */
public class POST_Raw_DraftPublish implements IPostRawHandler
{
	private final IEnvironment _environment;
	private final String _xsrf;
	private final LocalConfig _localConfig;
	private final IConnection _connection;
	private final INetworkScheduler _scheduler;
	private final BackgroundOperations _backgroundOperations;
	private final DraftManager _draftManager;
	
	public POST_Raw_DraftPublish(IEnvironment environment
			, String xsrf
			, LocalConfig localConfig
			, IConnection connection
			, INetworkScheduler scheduler
			, BackgroundOperations backgroundOperations
			, DraftManager draftManager
	)
	{
		_environment = environment;
		_xsrf = xsrf;
		_localConfig = localConfig;
		_connection = connection;
		_scheduler = scheduler;
		_backgroundOperations = backgroundOperations;
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			// Make sure there isn't already a publish update in-progress (later, we can just overwrite it).
			_backgroundOperations.waitForPendingPublish();
			
			IReadWriteLocalData data = _localConfig.getSharedLocalData().openForWrite();
			int draftId = Integer.parseInt(pathVariables[0]);
			try
			{
				FuturePublish asyncPublish = InteractiveHelpers.publishExistingDraft(_environment
						, data
						, _connection
						, _scheduler
						, _draftManager
						, draftId
				);
				InteractiveHelpers.deleteExistingDraft(_draftManager, draftId);
				
				// We can now wait for the publish to complete, now that we have closed all the local state.
				_backgroundOperations.waitAndStorePublishOperation(asyncPublish);
				_backgroundOperations.waitForPendingPublish();
				CommandHelpers.commonWaitForPublish(_environment, asyncPublish);
				
				response.setStatus(HttpServletResponse.SC_OK);
			}
			catch (FileNotFoundException e)
			{
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
			finally
			{
				// Allow the write-back of any updated state.
				// TODO:  Determine if we should be able to abandon this if something went wrong.
				data.close();
			}
		}
	}
}
