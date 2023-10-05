package com.jeffdisher.cacophony.caches;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A container of all HandoffConnector instances for entries for the local user and all followees.
 * Also exposes a "combined" connector which contains a union of this information.
 * Internally, each call is synchronized (and the connectors all use a common dispatcher), so this can be accessed
 * across the system, concurrently.
 */
public class EntryCacheRegistry implements IEntryCacheRegistry
{
	// The dispatcher passed in to the HandoffConnector instances.
	private final Consumer<Runnable> _dispatcher;
	// The connectors for entries associated with each user (home and followee), individually.
	private final Map<IpfsKey, HandoffConnector<IpfsFile, Void>> _perUserConnectors;
	// The combined connector for the union of all known posts.
	// Note that this doesn't exist until initializeCombinedView() is called (_bootstrapElements populated before then).
	private HandoffConnector<IpfsFile, Void> _combinedConnector;
	// The list of elements added during startup, before initializeCombinedView() is called.  This is so they can be
	// sorted correctly as the elements are not added to the combined connector in the order they were created or first
	// seen during start-up.
	private List<BootstrapTuple> _bootstrapElements;
	// The refcounts of all of the elements in _combinedConnector (not populated until initializeCombinedView()).
	private Map<IpfsFile, Integer> _combinedRefCounts;

	/**
	 * Creates the registry with the given dispatcher, using it to run changes against the HandofConnector instances
	 * created within.
	 * 
	 * @param dispatcher The dispatcher used by any HandoffConnector instances created by the registry.
	 */
	public EntryCacheRegistry(Consumer<Runnable> dispatcher)
	{
		_dispatcher = dispatcher;
		_perUserConnectors = new HashMap<>();
		_bootstrapElements = new ArrayList<>();
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
	 * @param publishedSecondsUtc The publication time of the element.
	 */
	public synchronized void addFolloweeElement(IpfsKey user, IpfsFile elementHash, long publishedSecondsUtc)
	{
		_perUserConnectors.get(user).create(elementHash, null);
		_addCombinedOrBuffer(elementHash, publishedSecondsUtc);
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
	 * @param publishedSecondsUtc The publication time of the element.
	 */
	public synchronized void addLocalElement(IpfsKey homeUserPublicKey, IpfsFile elementHash, long publishedSecondsUtc)
	{
		_perUserConnectors.get(homeUserPublicKey).create(elementHash, null);
		_addCombinedOrBuffer(elementHash, publishedSecondsUtc);
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
	 * Ends the bootstrapping phase of the receiver by creating the combined connector, with the time-sorted entries
	 * from the data fed in during the bootstrap phase.
	 * This MUST be called before getCombinedConnector().
	 */
	public synchronized void initializeCombinedView()
	{
		Assert.assertTrue(null != _bootstrapElements);
		_combinedConnector = new HandoffConnector<IpfsFile, Void>(_dispatcher);
		_combinedRefCounts = new HashMap<>();
		
		// Sort the list in publication order, then walk it to build the connector and combined refcounts.
		_bootstrapElements.sort(Comparator.comparingLong((BootstrapTuple tuple) -> tuple.publishedSecondsUtc));
		for (BootstrapTuple tuple : _bootstrapElements)
		{
			_addCombined(tuple.elt);
		}
		// Now, complete the bootstrap mode.
		_bootstrapElements = null;
	}

	@Override
	public synchronized HandoffConnector<IpfsFile, Void> getReadOnlyConnector(IpfsKey key)
	{
		return _perUserConnectors.get(key);
	}

	@Override
	public HandoffConnector<IpfsFile, Void> getCombinedConnector()
	{
		Assert.assertTrue(null != _combinedConnector);
		return _combinedConnector;
	}


	private void _addCombinedOrBuffer(IpfsFile elt, long publishedSecondsUtc)
	{
		if (null != _bootstrapElements)
		{
			_bootstrapElements.add(new BootstrapTuple(elt, publishedSecondsUtc));
		}
		else
		{
			_addCombined(elt);
		}
	}

	private void _addCombined(IpfsFile elt)
	{
		// This may be (1) something we already have referenced or (3) something new.
		int refCount = 0;
		boolean shouldCreate = false;
		if (_combinedRefCounts.containsKey(elt))
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


	private static record BootstrapTuple(IpfsFile elt, long publishedSecondsUtc) {}
}
