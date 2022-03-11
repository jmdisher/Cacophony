package com.jeffdisher.cacophony.utils;


/**
 * Utility class to make common assertion statement idioms more meaningful (and not something which can be disabled).
 */
public class Assert
{
	/**
	 * Called when an exception was not expected.  This should be for cases where the assertion is statically known to
	 * be not something which could happen.
	 * 
	 * @param e The unexpected exception.
	 * @return Does not return - this is only here so the caller can throw this to satisfy the compiler.
	 */
	public static AssertionError unexpected(Exception e)
	{
		throw new AssertionError("Unexpected exception", e);
	}

	/**
	 * A traditional assertion:  States that something must be true, failing if it isn't.
	 * 
	 * @param flag The statement which must be true.
	 */
	public static void assertTrue(boolean flag)
	{
		if (!flag)
		{
			throw new AssertionError("Expected true");
		}
	}

	/**
	 * Called when code which is statically never expected to be reachable is somehow executed.
	 * 
	 * @return Does not return - this is only here so the caller can throw this to satisfy the compiler.
	 */
	public static AssertionError unreachable()
	{
		throw new AssertionError("Unreachable code path hit");
	}

	/**
	 * Called when a feature which is only basically specced out for structure is used, even though it is not expected
	 * to be available in the current version of the system.
	 * 
	 * @param version The version of the software where this feature is expected to become available.
	 * @return Does not return - this is only here so the caller can throw this to satisfy the compiler.
	 */
	public static AssertionError unimplemented(int version)
	{
		throw new AssertionError("Unimplemented feature planned for version " + version);
	}
}
