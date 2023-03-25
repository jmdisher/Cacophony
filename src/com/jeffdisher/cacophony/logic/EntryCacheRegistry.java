package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A container of all HandoffConnector instances for entries for the local user and all followees.
 * Also exposes a "combined" connector which contains a union of this information (although it is a union with limited
 * history - limits the cost of start-up performance since the combined connector needs to read the elements to find
 * their publication time for sorting).
 * Internally, each call is synchronized (and the connectors all use a common dispatcher), so this can be accessed
 * across the system, concurrently.
 */
public class EntryCacheRegistry
{
	private final Consumer<Runnable> _dispatcher;
	private final IpfsKey _localUserKey;
	private final Map<IpfsKey, HandoffConnector<IpfsFile, Void>> _perUserConnectors;
	private final HandoffConnector<IpfsFile, Void> _combinedConnector;
	private final Map<IpfsFile, Integer> _combinedRefCounts;

	private EntryCacheRegistry(Consumer<Runnable> dispatcher
			, IpfsKey localUserKey
			, Map<IpfsKey
			, HandoffConnector<IpfsFile, Void>> perUserConnectors
			, HandoffConnector<IpfsFile, Void> combinedConnector
			, Map<IpfsFile, Integer> combinedRefCounts
	)
	{
		_dispatcher = dispatcher;
		_localUserKey = localUserKey;
		_perUserConnectors = perUserConnectors;
		_combinedConnector = combinedConnector;
		_combinedRefCounts = combinedRefCounts;
	}

	/**
	 * Sets the special string for the given user.
	 * 
	 * @param user The user (must be registered).
	 * @param message The message to set (can be null - typical in clearing).
	 */
	public synchronized void setSpecial(IpfsKey user, String message)
	{
		_perUserConnectors.get(user).setSpecial(message);
	}

	/**
	 * Adds a new element for the given followee.
	 * 
	 * @param user The followee.
	 * @param elementHash The new element's hash.
	 */
	public synchronized void addFolloweeElement(IpfsKey user, IpfsFile elementHash)
	{
		_perUserConnectors.get(user).create(elementHash, null);
		_addCombined(elementHash);
	}

	/**
	 * Removes an existing element for the given followee.
	 * 
	 * @param user The followee.
	 * @param elementHash The element's hash.
	 */
	public synchronized void removeFolloweeElement(IpfsKey followeeKey, IpfsFile elementHash)
	{
		// This isn't expected to be used for us.
		Assert.assertTrue(!_localUserKey.equals(followeeKey));
		_perUserConnectors.get(followeeKey).destroy(elementHash);
		_removeCombined(elementHash);
	}

	/**
	 * Adds a new element for the local user.
	 * 
	 * @param elementHash The new element's hash.
	 */
	public synchronized void addLocalElement(IpfsFile elementHash)
	{
		_perUserConnectors.get(_localUserKey).create(elementHash, null);
		_addCombined(elementHash);
	}

	/**
	 * Removes an existing element for the local user.
	 * 
	 * @param elementHash The element's hash.
	 */
	public synchronized void removeLocalElement(IpfsFile elementHash)
	{
		_perUserConnectors.get(_localUserKey).destroy(elementHash);
		_removeCombined(elementHash);
	}

	/**
	 * Registers a new followee.
	 * 
	 * @param userToAdd The key of the user to add.
	 */
	public synchronized void createNewFollowee(IpfsKey userToAdd)
	{
		_perUserConnectors.put(userToAdd, new HandoffConnector<IpfsFile, Void>(_dispatcher));
	}

	/**
	 * Unregisters an existing followee.
	 * 
	 * @param userToRemove The key of the user to remove.
	 */
	public synchronized void removeFollowee(IpfsKey userToRemove)
	{
		_perUserConnectors.remove(userToRemove);
	}

	/**
	 * Requests the reference to the connector for the given user key (followee or local).
	 * 
	 * @param key The user's key.
	 * @return The connector for this user.
	 */
	public synchronized HandoffConnector<IpfsFile, Void> getReadOnlyConnector(IpfsKey key)
	{
		return _perUserConnectors.get(key);
	}

	/**
	 * Requests the reference to the combined connector for all registered users.
	 * 
	 * @return The combined connector instance.
	 */
	public synchronized HandoffConnector<IpfsFile, Void> getCombinedConnector()
	{
		return _combinedConnector;
	}


	private void _addCombined(IpfsFile elt)
	{
		if (!_combinedRefCounts.containsKey(elt))
		{
			_combinedRefCounts.put(elt, 0);
			_combinedConnector.create(elt, null);
		}
		_combinedRefCounts.put(elt, _combinedRefCounts.get(elt) + 1);
	}

	private void _removeCombined(IpfsFile elt)
	{
		int refCount = _combinedRefCounts.get(elt);
		Assert.assertTrue(refCount > 0);
		refCount -= 1;
		if (0 == refCount)
		{
			_combinedRefCounts.remove(elt);
			_combinedConnector.destroy(elt);
		}
		else
		{
			_combinedRefCounts.put(elt, refCount);
		}
	}


	/**
	 * A factory used during initial start-up to pre-seed the data since start-up has a very different pattern from
	 * steady-state.
	 */
	public static class Builder
	{
		private final Consumer<Runnable> _dispatcher;
		private final Map<IpfsKey, HandoffConnector<IpfsFile, Void>> _perUserConnectors;
		private final Map<IpfsKey, List<IpfsFile>> _entriesToCombinePerUser;
		private final int _toCachePerUser;
		private boolean _done;
		
		/**
		 * Creates the new builder.
		 * 
		 * @param dispatcher The dispatcher to use for any created HandoffConnectors.
		 * @param toCachePerUser The limit of most recent entries posted by a user to be considered for the combined
		 * connector.
		 */
		public Builder(Consumer<Runnable> dispatcher, int toCachePerUser)
		{
			_dispatcher = dispatcher;
			_perUserConnectors = new HashMap<>();
			_entriesToCombinePerUser = new HashMap<>();
			_toCachePerUser = toCachePerUser;
		}
		
		/**
		 * Registers a user (local user or existing followee).
		 * 
		 * @param user The user's key.
		 */
		public void createConnector(IpfsKey user)
		{
			Assert.assertTrue(!_done);
			_perUserConnectors.put(user, new HandoffConnector<IpfsFile, Void>(_dispatcher));
		}
		
		/**
		 * Adds an element for a registered user (local or followee).
		 * 
		 * @param user The user's key.
		 * @param elementCid The element CID.
		 */
		public void addToUser(IpfsKey user, IpfsFile elementCid)
		{
			Assert.assertTrue(!_done);
			_perUserConnectors.get(user).create(elementCid, null);
			if (!_entriesToCombinePerUser.containsKey(user))
			{
				_entriesToCombinePerUser.put(user, new ArrayList<>());
			}
			List<IpfsFile> combine = _entriesToCombinePerUser.get(user);
			combine.add(elementCid);
			if (combine.size() > _toCachePerUser)
			{
				combine.remove(0);
			}
		}
		
		/**
		 * Called once start-up has completed to finish the creation of the registry.  This operation is quite expensive
		 * as it will consult the network in order to find the publication time of the most recent entries from each
		 * user so that it can determine how to sort them.
		 * Note that this renders the receiver unusable (as a way to catch bugs).
		 * 
		 * @param localUserKey The local user's key (must have been explicitly registered).
		 * @param recordLoader A function for loading requested elements from the network.
		 * @return The new registry.
		 * @throws IpfsConnectionException There was a problem contacting the network.
		 */
		public EntryCacheRegistry buildRegistry(IpfsKey localUserKey, Function<IpfsFile, FutureRead<StreamRecord>> recordLoader) throws IpfsConnectionException
		{
			Assert.assertTrue(!_done);
			Assert.assertTrue(_perUserConnectors.containsKey(localUserKey));
			// Walk the lists for user, combine them in one map with reference count (since there could be duplicates).
			Map<IpfsFile, Integer> elementsToCombine = new HashMap<>();
			for (List<IpfsFile> combine : _entriesToCombinePerUser.values())
			{
				for (IpfsFile elt : combine)
				{
					int count = elementsToCombine.getOrDefault(elt, 0);
					elementsToCombine.put(elt, count + 1);
				}
			}
			// Now, look these up and sort them by published time.
			List<Partial1> partials1 = new ArrayList<>();
			for (IpfsFile elt : elementsToCombine.keySet())
			{
				FutureRead<StreamRecord> future = recordLoader.apply(elt);
				partials1.add(new Partial1(elt, future));
			}
			List<Partial2> partials2 = new ArrayList<>();
			for (Partial1 part1 : partials1)
			{
				StreamRecord record;
				try
				{
					record = part1.future.get();
				}
				catch (IpfsConnectionException e)
				{
					throw e;
				}
				catch (FailedDeserializationException e)
				{
					// This is already cached.
					throw Assert.unexpected(e);
				}
				partials2.add(new Partial2(part1.elt, record.getPublishedSecondsUtc()));
			}
			partials2.sort(Comparator.comparingLong((Partial2 part2) -> part2.publishedSecondsUtc));
			HandoffConnector<IpfsFile, Void> combinedConnector = new HandoffConnector<IpfsFile, Void>(_dispatcher);
			for (Partial2 part2 : partials2)
			{
				combinedConnector.create(part2.elt, null);
			}
			// We set the _done flag just to avoid errors of the builder still being in use.
			// While it could be used to produce multiple registries, we don't use it that way so any further use is an error we want to catch.
			_done = true;
			return new EntryCacheRegistry(_dispatcher, localUserKey, _perUserConnectors, combinedConnector, elementsToCombine);
		}
	}


	private static record Partial1(IpfsFile elt, FutureRead<StreamRecord> future) {}
	private static record Partial2(IpfsFile elt, long publishedSecondsUtc) {}
}
