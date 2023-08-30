package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.MiscHelpers;


public record SetGlobalPrefsCommand(int _edgeMax
		, long _republishIntervalMillis
		, long _explicitCacheTargetBytes
		, long _explicitUserInfoRefreshMillis
		, long _followeeCacheTargetBytes
		, long _followeeRefreshMillis
		, long _followeeThumbnailMaxBytes
		, long _followeeAudioMaxBytes
		, long _followeeVideoMaxBytes
) implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			_runCore(context.logger, access);
		}
		return None.NONE;
	}


	private void _runCore(ILogger logger, IWritingAccess access) throws UsageException
	{
		PrefsData prefs = access.readPrefs();
		boolean didChange = false;
		
		if (_edgeMax > 0)
		{
			prefs.videoEdgePixelMax = _edgeMax;
			didChange = true;
		}
		if (_republishIntervalMillis > 0L)
		{
			prefs.republishIntervalMillis = _republishIntervalMillis;
			didChange = true;
		}
		if (_explicitCacheTargetBytes > 0L)
		{
			prefs.explicitCacheTargetBytes = _explicitCacheTargetBytes;
			didChange = true;
		}
		if (_explicitUserInfoRefreshMillis > 0L)
		{
			prefs.explicitUserInfoRefreshMillis = _explicitUserInfoRefreshMillis;
			didChange = true;
		}
		if (_followeeCacheTargetBytes > 0L)
		{
			prefs.followeeCacheTargetBytes = _followeeCacheTargetBytes;
			didChange = true;
		}
		if (_followeeRefreshMillis > 0L)
		{
			prefs.followeeRefreshMillis = _followeeRefreshMillis;
			didChange = true;
		}
		if (_followeeThumbnailMaxBytes > 0L)
		{
			prefs.followeeRecordThumbnailMaxBytes = _followeeThumbnailMaxBytes;
			didChange = true;
		}
		if (_followeeAudioMaxBytes > 0L)
		{
			prefs.followeeRecordAudioMaxBytes = _followeeAudioMaxBytes;
			didChange = true;
		}
		if (_followeeVideoMaxBytes > 0L)
		{
			prefs.followeeRecordVideoMaxBytes = _followeeVideoMaxBytes;
			didChange = true;
		}
		if (didChange)
		{
			access.writePrefs(prefs);
			ILogger log = logger.logStart("Preferences:");
			log.logOperation("Video preferred bounds: " + prefs.videoEdgePixelMax + " x " + prefs.videoEdgePixelMax);
			log.logOperation("Republish interval milliseconds: " + _republishIntervalMillis);
			log.logOperation("Explicit cache target size: " + MiscHelpers.humanReadableBytes(prefs.explicitCacheTargetBytes));
			log.logOperation("Explicit user info refresh milliseconds: " + _explicitUserInfoRefreshMillis);
			log.logOperation("Followee cache target size: " + MiscHelpers.humanReadableBytes(prefs.followeeCacheTargetBytes));
			log.logOperation("Followee record thumbnail max bytes: " + MiscHelpers.humanReadableBytes(prefs.followeeRecordThumbnailMaxBytes));
			log.logOperation("Followee record audio max bytes: " + MiscHelpers.humanReadableBytes(prefs.followeeRecordAudioMaxBytes));
			log.logOperation("Followee record video max bytes: " + MiscHelpers.humanReadableBytes(prefs.followeeRecordVideoMaxBytes));
			log.logFinish("Update saved");
		}
		else
		{
			throw new UsageException("Must specify a postive value for at least one of edge size or cache");
		}
	}
}
