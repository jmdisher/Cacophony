package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This command is just a combination of "create channel" and "start following" with the hard-coded demo channel key.
 * An optional "name" is exposed, as well.  If provided, this will be used as the channel name.
 * It is just to get things up and running quickly but this may be removed in the future, since it requires hard-coded
 * data and is inflexible.
 */
public record QuickstartCommand(String _keyName, String _optionalIpfsConnectString, String _optionalChannelName) implements ICommand
{
	/**
	 * This is the default API gateway connection used by IPFS.  This can be changed in the IPFS config but this will be
	 * the common case.
	 */
	private static final String DEFAULT_IPFS_CONNECT_STRING = "/ip4/127.0.0.1/tcp/5001";
	/**
	 * This is the Cacophony "demo channel" public key.
	 * Putting this in the code is not ideal since it is a real-world data value, but this command is only meant to be a
	 * convenient way for new users to test out the system, and not something critically important.
	 */
	private static final IpfsKey DEMO_CHANNEL_PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnJ6qSdFeWsMDaivGJPPCVx8jiopn9jK7aUThhuQjhERku");

	@Override
	public boolean requiresKey()
	{
		return true;
	}

	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		// Only the keyName must be provided as the other parameters are optional.
		Assert.assertTrue(null != _keyName);
		String ipfsConnectString = (null != _optionalIpfsConnectString)
				? _optionalIpfsConnectString
				: DEFAULT_IPFS_CONNECT_STRING
		;
		
		// We need to create the channel, no matter what else we plan to do.
		// This will throw UsageException if the channel already exists.
		IEnvironment.IOperationLog log = environment.logOperation("Quickstart:  Creating channel...");
		CreateChannelCommand createCommand = new CreateChannelCommand(ipfsConnectString, _keyName);
		createCommand.runInEnvironment(environment);
		log.finish("Done!");
		
		// We optionally want to update the channel description (just the name, using this path).
		if (null != _optionalChannelName)
		{
			log = environment.logOperation("Quickstart:  Setting channel name...");
			UpdateDescriptionCommand updateCommand = new UpdateDescriptionCommand(_optionalChannelName, null, null, null, null);
			updateCommand.runInEnvironment(environment);
			log.finish("Done!");
		}
		
		// Now, follow the demo channel.
		log = environment.logOperation("Quickstart:  Following demo channel (this could take several minutes)...");
		StartFollowingCommand followCommand = new StartFollowingCommand(DEMO_CHANNEL_PUBLIC_KEY);
		followCommand.runInEnvironment(environment);
		log.finish("Done!");
		
		environment.logToConsole("Channel is created and initial data has been loaded.  Run with \"--run\" to enable web server for interactive use.");
	}
}
