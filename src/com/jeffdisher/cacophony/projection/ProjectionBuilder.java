package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v2.IFolloweeDecoding;
import com.jeffdisher.cacophony.data.local.v2.IMiscUses;
import com.jeffdisher.cacophony.data.local.v2.OpcodeContext;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Utility class which builds our high-level data projections from a data opcode stream.
 */
public class ProjectionBuilder implements IMiscUses, IFolloweeDecoding
{
	public static Projections buildProjectionsFromOpcodeStream(InputStream rawData) throws IOException
	{
		ProjectionBuilder builder = new ProjectionBuilder();
		OpcodeContext context = new OpcodeContext(builder, builder);
		context.decodeWholeStream(rawData);
		return builder.getProjections();
	}


	private ChannelData _channel;
	private final FolloweeData _followees = FolloweeData.createEmpty();
	private final PinCacheData _pinCache = PinCacheData.createEmpty();
	private final PrefsData _prefs = PrefsData.defaultPrefs();

	public Projections getProjections()
	{
		return new Projections(_channel, _followees, _pinCache, _prefs);
	}

	@Override
	public void createConfig(String ipfsHost, String keyName)
	{
		Assert.assertTrue(null == _channel);
		_channel = ChannelData.create(ipfsHost, keyName);
	}

	@Override
	public void setLastPublishedIndex(IpfsFile lastPublishedIndex)
	{
		_channel.setLastPublishedIndex(lastPublishedIndex);
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
			_prefs.setVideoEdgePixelMax((Integer)value);
		}
		else if (keyName.equals(PrefsData.LONG_FOLLOW_CACHE_BYTES))
		{
			_prefs.setFollowCacheTargetBytes((Long)value);
		}
		else
		{
			throw Assert.unreachable();
		}
	}

	@Override
	public void createNewFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis)
	{
		_followees.createNewFollowee(followeeKey, indexRoot, lastPollMillis);
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
