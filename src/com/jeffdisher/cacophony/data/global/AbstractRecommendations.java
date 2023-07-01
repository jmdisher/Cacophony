package com.jeffdisher.cacophony.data.global;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
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
	public static final long SIZE_LIMIT_BYTES = SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES;
	public static final DataDeserializer<AbstractRecommendations> DESERIALIZER = (byte[] data) -> new AbstractRecommendations(_convertList(GlobalData.deserializeRecommendations(data).getUser()));

	/**
	 * @return A new empty recommendations list.
	 */
	public static AbstractRecommendations createNew()
	{
		return new AbstractRecommendations(Collections.emptyList());
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

	public void addUser(IpfsKey key)
	{
		_users.add(key);
	}

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
}
