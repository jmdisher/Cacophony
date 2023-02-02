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
}
