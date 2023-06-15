package com.jeffdisher.cacophony.scheduler;


/**
 * An interface common among some of the Future* classes in order for them to be verified as being consumed or at least
 * force to complete before proceeding, when the caller isn't concerned with the result.
 */
public interface IObservableFuture
{
	/**
	 * @return True if someone has observed the result of the future (meaning it completed).
	 */
	boolean wasObserved();

	/**
	 * Blocks the caller until the target has completed.
	 * Note that this marks the future as "observed".
	 */
	void waitForCompletion();
}
