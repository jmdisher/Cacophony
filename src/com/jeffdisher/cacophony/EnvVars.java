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

	/**
	 * Set to the IPFS connect string for accessing the IPFS API server (of the form "/ip4/127.0.0.1/tcp/5001").  If not
	 * set, a default will be assumed (which should work for nearly all non-testing use-cases).
	 */
	public static final String ENV_VAR_CACOPHONY_IPFS_CONNECT = "CACOPHONY_IPFS_CONNECT";

	/**
	 * Enables verbose console logging.  If not set, verbose logs will not be written to the console.
	 */
	public static final String ENV_VAR_CACOPHONY_VERBOSE = "CACOPHONY_VERBOSE";

	/**
	 * Temporary testing option:  Changes on-IPFS serialization to use version 2.
	 */
	public static final String ENV_VAR_CACOPHONY_TEST_NEW_DATA = "CACOPHONY_TEST_NEW_DATA";
}
