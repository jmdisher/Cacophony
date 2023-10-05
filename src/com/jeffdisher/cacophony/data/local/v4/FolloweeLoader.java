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

	/**
	 * Creates the instance to populate the given followees.
	 * 
	 * @param followees The data structure which should be populated by the loader.
	 */
	public FolloweeLoader(FolloweeData followees)
	{
		_followees = followees;
	}

	/**
	 * Defines a new followee and sets it as "current" so that the following elements are attached to it.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param indexRoot The root CID of the followee (when last fetched).
	 * @param lastPollMillis The time of the last poll attempt, in milliseconds since epoch.
	 * @param lastSuccessMillis The time of the last poll success, in milliseconds since epoch.
	 */
	public void createNewFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis, long lastSuccessMillis)
	{
		_followees.createNewFollowee(followeeKey, indexRoot, lastPollMillis, lastSuccessMillis);
		_currentFolloweeKey = followeeKey;
	}

	/**
	 * Adds a new element to the "current" followee.
	 * 
	 * @param elementHash The CID of the element.
	 * @param imageHash The CID of the thumbnail (could be null).
	 * @param leafHash The CID of the audio or video leaf (could be null).
	 * @param combinedSizeBytes The combined size, in bytes, of the thumbnail and leaf (does NOT include element size).
	 */
	public void addFolloweeElement(IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedSizeBytes)
	{
		_followees.addElement(_currentFolloweeKey, new FollowingCacheElement(elementHash, imageHash, leafHash, combinedSizeBytes));
	}

	/**
	 * Recods an element CID which was previously skipped for the "current" followee.
	 * 
	 * @param recordCid The CID of the element.
	 * @param isPermanent True if it was skipped for permanent reasons (corrupt data) or false for temporary reasons
	 * (network timeout).
	 */
	public void skipFolloweeRecord(IpfsFile recordCid, boolean isPermanent)
	{
		_followees.addSkippedRecord(_currentFolloweeKey, recordCid, isPermanent);
	}
}
