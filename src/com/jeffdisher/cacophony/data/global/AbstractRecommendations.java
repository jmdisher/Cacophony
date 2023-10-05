package com.jeffdisher.cacophony.data.global;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.v2.recommendations.CacophonyRecommendations;
import com.jeffdisher.cacophony.types.DataDeserializer;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * Meant to abstract the differences between StreamRecommendations (V1) and CacophonyRecommendations (V2) while also
 * providing a higher-level interface.
 */
public class AbstractRecommendations
{
	/**
	 * The maximum size, in bytes, of a recommendations file.
	 */
	public static final long SIZE_LIMIT_BYTES = SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES;
	/**
	 * The shared deserializer for reading an instance from raw data.
	 */
	public static final DataDeserializer<AbstractRecommendations> DESERIALIZER = (byte[] data) -> _commonMultiVersionLoad(data);

	/**
	 * @return A new empty recommendations list.
	 */
	public static AbstractRecommendations createNew()
	{
		return new AbstractRecommendations(Collections.emptyList());
	}


	private static AbstractRecommendations _commonMultiVersionLoad(byte[] data) throws FailedDeserializationException
	{
		AbstractRecommendations converted;
		try
		{
			// We check for version 2, first.
			CacophonyRecommendations recordsV2 = GlobalData2.deserializeRecommendations(data);
			converted = new AbstractRecommendations(_convertList(recordsV2.getUser()));
		}
		catch (FailedDeserializationException e)
		{
			// We will try version 1.
			StreamRecommendations recordsV1 = GlobalData.deserializeRecommendations(data);
			converted = new AbstractRecommendations(_convertList(recordsV1.getUser()));
		}
		
		// We would have loaded one of them or thrown.
		return converted;
	}

	private static List<IpfsKey> _convertList(List<String> raw) throws FailedDeserializationException
	{
		// We want to make sure that none of these fail to convert.
		List<IpfsKey> processed = new ArrayList<>();
		for (String one : raw)
		{
			IpfsKey file = IpfsKey.fromPublicKey(one);
			if (null == file)
			{
				throw new FailedDeserializationException(StreamRecommendations.class);
			}
			processed.add(file);
		}
		return processed;
	}


	private final List<IpfsKey> _users;

	private AbstractRecommendations(List<IpfsKey> list)
	{
		_users = new ArrayList<>();
		_users.addAll(list);
	}

	/**
	 * @return An unmodifiable list of the users.
	 */
	public List<IpfsKey> getUserList()
	{
		return Collections.unmodifiableList(_users);
	}

	/**
	 * Removes the given victim from the list, returning true if they were removed or false for not found.
	 * 
	 * @param victim The user to remove.
	 * @return True if the user was removed or false if it was not found.
	 */
	public boolean removeUser(IpfsKey victim)
	{
		return _users.remove(victim);
	}

	/**
	 * Adds the given user to the recommended user list.
	 * 
	 * @param key The public key of the user to add.
	 */
	public void addUser(IpfsKey key)
	{
		_users.add(key);
	}

	/**
	 * Serializes the instance as a V1 StreamRecommendations, returning the resulting byte array.
	 * 
	 * @return The byte array of the serialized instance
	 * @throws SizeConstraintException The instance was too big to fit within limits, once serialized.
	 */
	public byte[] serializeV1() throws SizeConstraintException
	{
		StreamRecommendations recommendations = new StreamRecommendations();
		List<String> raw = recommendations.getUser();
		for (IpfsKey ref : _users)
		{
			raw.add(ref.toPublicKey());
		}
		return GlobalData.serializeRecommendations(recommendations);
	}

	/**
	 * Serializes the instance as a V2 CacophonyRecommendations, returning the resulting byte array.
	 * 
	 * @return The byte array of the serialized instance
	 * @throws SizeConstraintException The instance was too big to fit within limits, once serialized.
	 */
	public byte[] serializeV2() throws SizeConstraintException
	{
		CacophonyRecommendations recommendations = new CacophonyRecommendations();
		List<String> raw = recommendations.getUser();
		for (IpfsKey ref : _users)
		{
			raw.add(ref.toPublicKey());
		}
		return GlobalData2.serializeRecommendations(recommendations);
	}
}
