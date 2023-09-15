package com.jeffdisher.cacophony.data.local.v4;

import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The followee data requires some stateful information to be loaded.  This class hangs off of the OpcodeContext and
 * implements that state data.
 */
public class FolloweeLoader
{
	private final FolloweeData _followees;
	private IpfsKey _currentFolloweeKey;

	public FolloweeLoader(FolloweeData followees)
	{
		_followees = followees;
	}

	public void createNewFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis, long lastSuccessMillis)
	{
		_followees.createNewFollowee(followeeKey, indexRoot, lastPollMillis, lastSuccessMillis);
		_currentFolloweeKey = followeeKey;
	}

	public void addFolloweeElement(IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedSizeBytes)
	{
		_followees.addElement(_currentFolloweeKey, new FollowingCacheElement(elementHash, imageHash, leafHash, combinedSizeBytes));
	}

	public void skipFolloweeRecord(IpfsFile recordCid, boolean isPermanent)
	{
		_followees.addSkippedRecord(_currentFolloweeKey, recordCid, isPermanent);
	}
}
