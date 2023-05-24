package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v2.IFolloweeDecoding;
import com.jeffdisher.cacophony.data.local.v2.IMiscUses;
import com.jeffdisher.cacophony.data.local.v2.OpcodeContext;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.KeyNameRules;


/**
 * Utility class which builds our high-level data projections from a data opcode stream.
 */
public class ProjectionBuilder implements IMiscUses, IFolloweeDecoding
{
	public static Projections buildProjectionsFromOpcodeStream(INetworkScheduler scheduler, InputStream rawData) throws IOException
	{
		ProjectionBuilder builder = new ProjectionBuilder(scheduler);
		OpcodeContext context = new OpcodeContext(builder, builder);
		context.decodeWholeStream(rawData);
		return builder.getProjections();
	}


	private final INetworkScheduler _scheduler;
	private final ChannelData _channel;
	private final FolloweeData _followees;
	private final PinCacheData _pinCache;
	private final PrefsData _prefs;
	private String _keyName;

	public ProjectionBuilder(INetworkScheduler scheduler)
	{
		_scheduler = scheduler;
		_channel = ChannelData.create();
		_followees = FolloweeData.createEmpty();
		_pinCache = PinCacheData.createEmpty();
		_prefs = PrefsData.defaultPrefs();
	}

	public Projections getProjections()
	{
		return new Projections(_channel, _followees, _pinCache, _prefs);
	}

	@Override
	public void createConfig(String ipfsHost, String keyName)
	{
		// This is a version 2 data model so assert that we didn't already define the singular channel.
		Assert.assertTrue(_channel.getKeyNames().isEmpty());
		Assert.assertTrue(null == _keyName);
		// This would be invalid data.
		Assert.assertTrue(KeyNameRules.isValidKey(keyName));
		// We just store the name and populate the config later (that way, if there is no root, we don't define the channel).
		_keyName = keyName;
	}

	@Override
	public void setLastPublishedIndex(IpfsFile lastPublishedIndex)
	{
		// This is a version 2 data model so assert that we didn't already define the singular channel.
		Assert.assertTrue(_channel.getKeyNames().isEmpty());
		Assert.assertTrue(null != _keyName);
		
		// We need to look up the key - later models store this locally, for convenience, but the older model doesn't.
		IpfsKey publicKey;
		try
		{
			publicKey = _scheduler.getOrCreatePublicKey(_keyName).get();
		}
		catch (IpfsConnectionException e)
		{
			// This is only now called during migration so we won't sweat not having an elegant way to plumb the error and just fail.
			throw Assert.unexpected(e);
		}
		_channel.initializeChannelState(_keyName, publicKey, lastPublishedIndex);
	}

	@Override
	public void setPinnedCount(IpfsFile cid, int count)
	{
		for (int i = 0; i < count; ++i)
		{
			_pinCache.addRef(cid);
		}
	}

	@Override
	public void setPrefsKey(String keyName, Serializable value)
	{
		if (keyName.equals(PrefsData.INT_VIDEO_EDGE))
		{
			_prefs.videoEdgePixelMax = (Integer)value;
		}
		else if (keyName.equals(PrefsData.LONG_FOLLOW_CACHE_BYTES))
		{
			_prefs.followCacheTargetBytes = (Long)value;
		}
		else if (keyName.equals(PrefsData.LONG_REPUBLISH_INTERVAL_MILLIS))
		{
			_prefs.republishIntervalMillis = (Long)value;
		}
		else if (keyName.equals(PrefsData.LONG_FOLLOWEE_REFRESH_MILLIS))
		{
			_prefs.followeeRefreshMillis = (Long)value;
		}
		else
		{
			throw Assert.unreachable();
		}
	}

	@Override
	public void createNewFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis)
	{
		_followees.createNewFollowee(followeeKey, indexRoot);
		// If this has a non-zero time, update the update time.
		if (lastPollMillis > 0L)
		{
			_followees.updateExistingFollowee(followeeKey, indexRoot, lastPollMillis);
		}
	}

	@Override
	public void addElement(IpfsKey followeeKey, IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedSizeBytes)
	{
		_followees.addElement(followeeKey, new FollowingCacheElement(elementHash, imageHash, leafHash, combinedSizeBytes));
	}


	public static record Projections(ChannelData channel, FolloweeData followee, PinCacheData pinCache, PrefsData prefs)
	{
	}
}
