package com.jeffdisher.cacophony;


/**
 * Just contains the environment variables the system checks.
 */
public class EnvVars
{
	/**
	 * If set, this directory path will be used for the user's Cacophony data storage.  Defaults to "~/.cacophony" if
	 * not set.
	 */
	public static final String ENV_VAR_CACOPHONY_STORAGE = "CACOPHONY_STORAGE";

	/**
	 * Set to enable additional internal verifications which are normally disabled.  Enabling this may slightly reduce
	 * performance.
	 */
	public static final String ENV_VAR_CACOPHONY_ENABLE_VERIFICATIONS = "CACOPHONY_ENABLE_VERIFICATIONS";

	/**
	 * The name of the key used for this user.  Defaults to "Cacophony" is not set.
	 */
	public static final String ENV_VAR_CACOPHONY_KEY_NAME = "CACOPHONY_KEY_NAME";

	/**
	 * Set to enable the in-memory "fake" IPFS network and local data store.  This is done to make certain kinds of
	 * testing more reliable and lighter-weight.
	 * The value given is interpreted as a directory which can be used for large file storage, etc (drafts, currently).
	 * When enabled, neither the real IPFS network nor the on-disk data store will be used.
	 */
	public static final String ENV_VAR_CACOPHONY_ENABLE_FAKE_SYSTEM = "CACOPHONY_ENABLE_FAKE_SYSTEM";
}
