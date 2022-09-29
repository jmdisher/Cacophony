package com.jeffdisher.cacophony.utils;


/**
 * We want to impose maximum limits on meta-data elements before we fetch them locally.
 * It is possible for a nefarious user to attack the system by uploading gratuitously large/corrupt/invalid data but
 * they could only effect their followers this way so the system doesn't need to (and also can't) do anything to prevent
 * this.
 * The main reason for this is to protect against incorrectly uploaded or referenced data.
 */
public class SizeLimits
{
	/**
	 * The size of the root data structure (StreamIndex).
	 * This is a very small file so 1 KiB is a reasonable limit.
	 */
	public static final long MAX_INDEX_SIZE_BYTES = 1024L;

	/**
	 * The size of the description data structure (StreamDescription).
	 * This is similar to a StreamRecord, in that it is largely human-written.  In practice, it is smaller (no unbounded
	 * references) so we will just use 64 KiB.
	 */
	public static final long MAX_DESCRIPTION_SIZE_BYTES = 64 * 1024L;

	/**
	 * The size of the meta-data structures which are ultimately just lists of references (StreamRecommendations and
	 * StreamRecords).
	 * These can be unbounded in size, but are typically very small, so we will set the max to 128 KiB.
	 */
	public static final long MAX_META_DATA_LIST_SIZE_BYTES = 128 * 1024L;

	/**
	 * The size of the element data structure (StreamRecord).
	 * This file can be somewhat large if it offers many different versions of the same element so 128 KiB seems
	 * reasonable.
	 */
	public static final long MAX_RECORD_SIZE_BYTES = 128 * 1024L;

	/**
	 * The size of the "user pic" referenced by the user's description.  Images can be large, and this rarely changes,
	 * so we will set it to 2 MiB.
	 */
	public static final long MAX_DESCRIPTION_IMAGE_SIZE_BYTES = 2 * 1024L * 1024L;
}
