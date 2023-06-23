package com.jeffdisher.cacophony.data.global;

import com.jeffdisher.cacophony.data.global.v2.extensions.CacophonyExtensionVideo;
import com.jeffdisher.cacophony.data.global.v2.record.CacophonyRecord;


/**
 * Used as a high-level container of the relevant high-level parts of a record.  The CacophonyRecord is never null but
 * the CacophonyExtensionVideo is optional.
 */
public record CacophonyExtendedRecord(CacophonyRecord record
		, CacophonyExtensionVideo video
)
{
}
