package com.jeffdisher.cacophony.actions;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.ChannelModifier;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Helpers which apply some static assumptions to the system in order to restrict the exception error cases to what they
 * can _actually_ be, when running on the local user.
 */
public class ActionHelpers
{
	public static StreamRecommendations readRecommendations(ChannelModifier modifier) throws IpfsConnectionException
	{
		try
		{
			return modifier.loadRecommendations();
		}
		catch (IpfsConnectionException e)
		{
			throw e;
		}
		catch (FailedDeserializationException e)
		{
			// This is for deserializing the local channel so the error isn't expected.
			throw Assert.unexpected(e);
		}
	}

	public static StreamRecords readRecords(ChannelModifier modifier) throws IpfsConnectionException
	{
		try
		{
			return modifier.loadRecords();
		}
		catch (IpfsConnectionException e)
		{
			throw e;
		}
		catch (FailedDeserializationException e)
		{
			// This is for deserializing the local channel so the error isn't expected.
			throw Assert.unexpected(e);
		}
	}

	public static StreamDescription readDescription(ChannelModifier modifier) throws IpfsConnectionException
	{
		try
		{
			return modifier.loadDescription();
		}
		catch (IpfsConnectionException e)
		{
			throw e;
		}
		catch (FailedDeserializationException e)
		{
			// This is for deserializing the local channel so the error isn't expected.
			throw Assert.unexpected(e);
		}
	}

	public static IpfsFile commitNewRoot(ChannelModifier modifier) throws IpfsConnectionException
	{
		try
		{
			return modifier.commitNewRoot();
		}
		catch (IpfsConnectionException e)
		{
			throw e;
		}
		catch (SizeConstraintException e)
		{
			// If we hit this failure, there is either something seriously corrupt or the spec needs to be updated.
			throw Assert.unexpected(e);
		}
	}

	public static <T> T unwrap(FutureRead<T> future) throws IpfsConnectionException
	{
		try
		{
			return future.get();
		}
		catch (IpfsConnectionException e)
		{
			throw e;
		}
		catch (FailedDeserializationException e)
		{
			// This is for deserializing the local channel so the error isn't expected.
			throw Assert.unexpected(e);
		}
	}

	public static byte[] serializeRecord(StreamRecord record)
	{
		try
		{
			return GlobalData.serializeRecord(record);
		}
		catch (SizeConstraintException e)
		{
			// This would be a static error we should be handling on a higher level so ending up here would be a bug.
			throw Assert.unexpected(e);
		}
	}
}
