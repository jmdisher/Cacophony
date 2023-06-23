package com.jeffdisher.cacophony.utils;


/**
 * We want to impose maximum limits on meta-data elements before we fetch them locally.
 * It is possible for a nefarious user to attack the system by uploading gratuitously large/corrupt/invalid data but
 * they could only effect their followers this way so the system doesn't need to (and also can't) do anything to prevent
 * this.
 * The main reason for this is to protect against incorrectly uploaded or referenced data.
 */
public class SizeLimits2
{
	/**
	 * The size of the root data structure (CacophonyRoot).
	 * We will use 16 KiB since these are normally small but could have a large number of data types, as of version2.
	 */
	public static final long MAX_ROOT_SIZE_BYTES = 16 * 1024L;

	/**
	 * The size of the description data structure (CacophonyDescription).
	 * This is similar to a CacophonyRecord, in that it is largely human-written.  In practice, it is smaller (no
	 * unbounded references) so we will just use 64 KiB.
	 */
	public static final long MAX_DESCRIPTION_SIZE_BYTES = 64 * 1024L;

	/**
	 * The size of the "user pic" referenced by the user's description.  Images can be large, and this rarely changes,
	 * so we will set it to 2 MiB.
	 */
	public static final long MAX_DESCRIPTION_IMAGE_SIZE_BYTES = 2 * 1024L * 1024L;

	/**
	 * The maximum size of the recommendations reference list structure (CacophonyRecommendations).
	 * These are typically small but could be unbounded so we will set the max to 128 KiB.
	 */
	public static final long MAX_RECOMMENDATIONS_SIZE_BYTES = 128 * 1024L;

	/**
	 * The maximum size of the record reference list structure (CacophonyRecords).
	 * These are small for most users but can grow to be unbounded and could reasonably become large for some users so
	 * we set this limit at 1 MiB.
	 */
	public static final long MAX_RECORDS_SIZE_BYTES = 1024 * 1024L;

	/**
	 * The size of the element data structure (CacophonyRecord).
	 * This file can be somewhat large if it offers many different versions of the same element so 128 KiB seems
	 * reasonable.
	 */
	public static final long MAX_RECORD_SIZE_BYTES = 128 * 1024L;
}
