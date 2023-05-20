package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This command is just a combination of "create channel" and "start following" with the hard-coded demo channel key.
 * An optional "name" is exposed, as well.  If provided, this will be used as the channel name.
 * It is just to get things up and running quickly but this may be removed in the future, since it requires hard-coded
 * data and is inflexible.
 */
public record QuickstartCommand(String _optionalChannelName) implements ICommand<ChangedRoot>
{
	/**
	 * This is the Cacophony "demo channel" public key.
	 * Putting this in the code is not ideal since it is a real-world data value, but this command is only meant to be a
	 * convenient way for new users to test out the system, and not something critically important.
	 */
	private static final IpfsKey DEMO_CHANNEL_PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnJ6qSdFeWsMDaivGJPPCVx8jiopn9jK7aUThhuQjhERku");

	@Override
	public ChangedRoot runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		// There is always a key set.
		Assert.assertTrue(null != context.keyName);
		
		// We need to create the channel, no matter what else we plan to do.
		// This will throw UsageException if the channel already exists.
		ILogger log = context.logger.logStart("Quickstart:  Creating channel...");
		CreateChannelCommand createCommand = new CreateChannelCommand();
		IpfsFile finalUpdatedRoot = createCommand.runInContext(context).getIndexToPublish();
		log.logFinish("Done!");
		
		// We optionally want to update the channel description (just the name, using this path).
		if (null != _optionalChannelName)
		{
			log = context.logger.logStart("Quickstart:  Setting channel name...");
			UpdateDescriptionCommand updateCommand = new UpdateDescriptionCommand(_optionalChannelName, null, null, null, null);
			finalUpdatedRoot = updateCommand.runInContext(context).getIndexToPublish();
			log.logFinish("Done!");
		}
		
		// Now, follow the demo channel.
		log = context.logger.logStart("Quickstart:  Following demo channel (this could take several minutes)...");
		StartFollowingCommand followCommand = new StartFollowingCommand(DEMO_CHANNEL_PUBLIC_KEY);
		try
		{
			followCommand.runInContext(context);
		}
		catch (ProtocolDataException e)
		{
			// We don't expect this to happen since the demo channel should be well-formed.  This could be a future version, though.
			log.logOperation("WARNING:  Demo channel data was invalid.  You may need to update to a later version.");
		}
		catch (KeyException e)
		{
			log.logOperation("WARNING:  Demo channel could not be found.  It has not been added to your followee list.");
		}
		log.logFinish("Done!");
		
		context.logger.logVerbose("Channel is created and initial data has been loaded.  Run with \"--run\" to enable web server for interactive use.");
		return new ChangedRoot(finalUpdatedRoot);
	}
}
