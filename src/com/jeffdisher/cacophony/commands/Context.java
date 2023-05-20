package com.jeffdisher.cacophony.commands;

import java.net.URL;

import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * A container of resources which can be used by a command.
 * Instances of this type are expected to configured for sub-commands, etc, based on resources available at the
 * top-level.
 */
public class Context
{
	public final IEnvironment environment;
	public final ILogger logger;
	public final URL baseUrl;
	public final LocalRecordCache recordCache;
	public final LocalUserInfoCache userInfoCache;
	public final EntryCacheRegistry entryRegistry;
	public final String keyName;
	// The public key of the context, since it can be set when the channel is created.
	public IpfsKey publicKey;
	
	public Context(IEnvironment environment
			, ILogger logger
			, URL baseUrl
			, LocalRecordCache recordCache
			, LocalUserInfoCache userInfoCache
			, EntryCacheRegistry entryRegistry
			, String keyName
			, IpfsKey publicKey
	)
	{
		this.environment = environment;
		this.logger = logger;
		this.baseUrl = baseUrl;
		this.recordCache = recordCache;
		this.userInfoCache = userInfoCache;
		this.entryRegistry = entryRegistry;
		this.keyName = keyName;
		this.publicKey = publicKey;
	}
}
