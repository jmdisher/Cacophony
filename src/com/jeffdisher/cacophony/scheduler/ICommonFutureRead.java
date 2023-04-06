package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.SizeConstraintException;


/**
 * This interface is meant to capture the get() call of FutureSizedRead<D> so that SyntheticRead<D> can be used to wrap
 * FutureRead<D>, in the cases where we want common logic for both cases of reads (since the logic around them is often
 * the same).
 * 
 * @param <D> The data type being read and decoded.
 */
public interface ICommonFutureRead<D>
{
	/**
	 * Blocks for the asynchronous operation to complete.
	 * 
	 * @return The successful result of the request.
	 * @throws IpfsConnectionException The exception which caused the read to fail.
	 * @throws SizeConstraintException The size check failed.
	 * @throws FailedDeserializationException There was a failure in decoding the data after loading.
	 */
	D get() throws IpfsConnectionException, SizeConstraintException, FailedDeserializationException;
}
