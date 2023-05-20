package com.jeffdisher.cacophony.commands;

import java.util.Set;

import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public record ListChannelsCommand() implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException
	{
		// We want a pretty low-level read which doesn't take into account the current context key so use the data model, directly.
		LocalDataModel dataModel = context.environment.getSharedDataModel();
		try (IReadOnlyLocalData reading = dataModel.openForRead())
		{
			ChannelData data = reading.readLocalIndex();
			Set<String> keyNames = data.getKeyNames();
			ILogger log = context.logger.logStart("Found " + keyNames.size() + " channels:");
			for (String keyName : keyNames)
			{
				IpfsKey publicKey = data.getPublicKey(keyName);
				IpfsFile lastRoot = data.getLastPublishedIndex(keyName);
				boolean isSelected = keyName.equals(context.keyName);
				
				ILogger one = log.logStart("Key name: " + keyName + (isSelected ? " (SELECTED)" : ""));
				one.logOperation("Public key: " + publicKey);
				one.logOperation("Last published root: " + lastRoot);
				one.logFinish("");
			}
			log.logFinish("");
		}
		return None.NONE;
	}
}
