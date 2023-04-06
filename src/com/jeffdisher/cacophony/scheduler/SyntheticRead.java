package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.SizeConstraintException;


/**
 * An implementation of ICommonFutureRead<D> which adapts FutureRead<D> for use where this common interface is
 * reasonable.
 * 
 * @param <D> The data type being read and decoded.
 */
public class SyntheticRead<D> implements ICommonFutureRead<D>
{
	private final FutureRead<D> _read;
	public SyntheticRead(FutureRead<D> read)
	{
		_read = read;
	}
	@Override
	public D get() throws IpfsConnectionException, SizeConstraintException, FailedDeserializationException
	{
		return _read.get();
	}
}
