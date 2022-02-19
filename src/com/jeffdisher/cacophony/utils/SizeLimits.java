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
	 * The size of the element data structure (StreamRecord).
	 * This file can be somewhat large if it offers many different versions of the same element so 128 KiB seems
	 * reasonable.
	 */
	public static final long MAX_RECORD_SIZE_BYTES = 128 * 1024L;
}
