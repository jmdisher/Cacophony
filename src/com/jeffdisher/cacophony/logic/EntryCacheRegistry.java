package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.cacophony.data.global.AbstractRecord;
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
	private final Map<IpfsKey, HandoffConnector<IpfsFile, Void>> _perUserConnectors;
	private final HandoffConnector<IpfsFile, Void> _combinedConnector;
	private final Map<IpfsFile, Integer> _combinedRefCounts;
	private final Map<IpfsFile, Integer> _spilledRefCounts;

	private EntryCacheRegistry(Consumer<Runnable> dispatcher
			, Map<IpfsKey
			, HandoffConnector<IpfsFile, Void>> perUserConnectors
			, HandoffConnector<IpfsFile, Void> combinedConnector
			, Map<IpfsFile, Integer> combinedRefCounts
			, Map<IpfsFile, Integer> spilledRefCounts
	)
	{
		_dispatcher = dispatcher;
		_perUserConnectors = perUserConnectors;
		_combinedConnector = combinedConnector;
		_combinedRefCounts = combinedRefCounts;
		_spilledRefCounts = spilledRefCounts;
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
		_perUserConnectors.get(followeeKey).destroy(elementHash);
		_removeCombined(elementHash);
	}

	/**
	 * Adds a new element for the local user.
	 * 
	 * @param homeUserPublicKey The public key of the home user.
	 * @param elementHash The new element's hash.
	 */
	public synchronized void addLocalElement(IpfsKey homeUserPublicKey, IpfsFile elementHash)
	{
		_perUserConnectors.get(homeUserPublicKey).create(elementHash, null);
		_addCombined(elementHash);
	}

	/**
	 * Removes an existing element for the local user.
	 * 
	 * @param homeUserPublicKey The public key of the home user.
	 * @param elementHash The element's hash.
	 */
	public synchronized void removeLocalElement(IpfsKey homeUserPublicKey, IpfsFile elementHash)
	{
		_perUserConnectors.get(homeUserPublicKey).destroy(elementHash);
		_removeCombined(elementHash);
	}

	/**
	 * Registers a new home user.
	 * 
	 * @param userToAdd The key of the user to add.
	 */
	public synchronized void createHomeUser(IpfsKey userToAdd)
	{
		Assert.assertTrue(!_perUserConnectors.containsKey(userToAdd));
		_perUserConnectors.put(userToAdd, new HandoffConnector<IpfsFile, Void>(_dispatcher));
	}

	/**
	 * Unregisters an existing home user.
	 * 
	 * @param userToRemove The key of the user to remove.
	 */
	public synchronized void removeHomeUser(IpfsKey userToRemove)
	{
		Assert.assertTrue(_perUserConnectors.containsKey(userToRemove));
		_perUserConnectors.remove(userToRemove);
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
		// This may be (1) something we previously spilled, (2) something we already have referenced, or (3) something new.
		int refCount = 0;
		boolean shouldCreate = false;
		if (_spilledRefCounts.containsKey(elt))
		{
			// Move this into the combined count and add 1.
			refCount = _spilledRefCounts.remove(elt);
			shouldCreate = true;
		}
		else if (_combinedRefCounts.containsKey(elt))
		{
			// We don't need to create anything, just increment the count.
			refCount = _combinedRefCounts.get(elt);
		}
		else
		{
			// New, so we will need to notify.
			shouldCreate = true;
		}
		_combinedRefCounts.put(elt, refCount + 1);
		if (shouldCreate)
		{
			_combinedConnector.create(elt, null);
		}
	}

	private void _removeCombined(IpfsFile elt)
	{
		// This may be (1) something we previously spilled or (2) something we already have referenced.
		if (_spilledRefCounts.containsKey(elt))
		{
			// Just update the count but don't notify.
			int refCount = _spilledRefCounts.remove(elt);
			refCount -= 1;
			if (refCount > 0)
			{
				_spilledRefCounts.put(elt, refCount);
			}
		}
		else
		{
			Assert.assertTrue(_combinedRefCounts.containsKey(elt));
			// Decrement the count and notify if 0.
			int refCount = _combinedRefCounts.remove(elt);
			refCount -= 1;
			if (refCount > 0)
			{
				_combinedRefCounts.put(elt, refCount);
			}
			else
			{
				_combinedConnector.destroy(elt);
			}
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
		private final Map<IpfsFile, Integer> _spilledRefCount;
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
			_spilledRefCount = new HashMap<>();
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
			Assert.assertTrue(!_perUserConnectors.containsKey(user));
			_perUserConnectors.put(user, new HandoffConnector<IpfsFile, Void>(_dispatcher));
			Assert.assertTrue(!_entriesToCombinePerUser.containsKey(user));
			_entriesToCombinePerUser.put(user, new ArrayList<>());
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
			List<IpfsFile> combine = _entriesToCombinePerUser.get(user);
			combine.add(elementCid);
			if (combine.size() > _toCachePerUser)
			{
				// We will remove this from the list we want to check in the network, but we still need to make sure we properly refcount it for the combined list, in case it is deleted.
				IpfsFile spilled = combine.remove(0);
				int refCount = _spilledRefCount.getOrDefault(spilled, 0);
				_spilledRefCount.put(spilled, refCount + 1);
			}
		}
		
		/**
		 * Called once start-up has completed to finish the creation of the registry.  This operation is quite expensive
		 * as it will consult the network in order to find the publication time of the most recent entries from each
		 * user so that it can determine how to sort them.
		 * Note that this renders the receiver unusable (as a way to catch bugs).
		 * 
		 * @param recordLoader A function for loading requested elements from the network.
		 * @return The new registry.
		 * @throws IpfsConnectionException There was a problem contacting the network.
		 */
		public EntryCacheRegistry buildRegistry(Function<IpfsFile, FutureRead<AbstractRecord>> recordLoader) throws IpfsConnectionException
		{
			Assert.assertTrue(!_done);
			// Walk the lists for user, combine them in one map with reference count (since there could be duplicates).
			Set<IpfsFile> elementsToCombine = new HashSet<>();
			Map<IpfsFile, Integer> combinedRefCounts = new HashMap<>();
			for (List<IpfsFile> combine : _entriesToCombinePerUser.values())
			{
				for (IpfsFile elt : combine)
				{
					elementsToCombine.add(elt);
					int count = combinedRefCounts.getOrDefault(elt, 0);
					combinedRefCounts.put(elt, count + 1);
				}
			}
			// Now, look these up and sort them by published time.
			List<Partial1> partials1 = new ArrayList<>();
			for (IpfsFile elt : elementsToCombine)
			{
				FutureRead<AbstractRecord> future = recordLoader.apply(elt);
				partials1.add(new Partial1(elt, future));
			}
			List<Partial2> partials2 = new ArrayList<>();
			for (Partial1 part1 : partials1)
			{
				AbstractRecord record;
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
			return new EntryCacheRegistry(_dispatcher, _perUserConnectors, combinedConnector, combinedRefCounts, _spilledRefCount);
		}
	}


	private static record Partial1(IpfsFile elt, FutureRead<AbstractRecord> future) {}
	private static record Partial2(IpfsFile elt, long publishedSecondsUtc) {}
}
