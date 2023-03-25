package com.jeffdisher.cacophony.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A container of all HandoffConnector instances for entries for the local user and all followees.
 * Internally, each call is synchronized (and the connectors all use a common dispatcher), so this can be accessed
 * across the system, concurrently.
 */
public class EntryCacheRegistry
{
	private final Consumer<Runnable> _dispatcher;
	private final IpfsKey _localUserKey;
	private final Map<IpfsKey, HandoffConnector<IpfsFile, Void>> _perUserConnectors;

	private EntryCacheRegistry(Consumer<Runnable> dispatcher
			, IpfsKey localUserKey
			, Map<IpfsKey
			, HandoffConnector<IpfsFile, Void>> perUserConnectors
	)
	{
		_dispatcher = dispatcher;
		_localUserKey = localUserKey;
		_perUserConnectors = perUserConnectors;
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
	}

	/**
	 * Adds a new element for the local user.
	 * 
	 * @param elementHash The new element's hash.
	 */
	public synchronized void addLocalElement(IpfsFile elementHash)
	{
		_perUserConnectors.get(_localUserKey).create(elementHash, null);
	}

	/**
	 * Removes an existing element for the local user.
	 * 
	 * @param elementHash The element's hash.
	 */
	public synchronized void removeLocalElement(IpfsFile elementHash)
	{
		_perUserConnectors.get(_localUserKey).destroy(elementHash);
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
	 * A factory used during initial start-up to pre-seed the data since start-up has a very different pattern from
	 * steady-state.
	 */
	public static class Builder
	{
		private final Consumer<Runnable> _dispatcher;
		private final Map<IpfsKey, HandoffConnector<IpfsFile, Void>> _perUserConnectors;
		private boolean _done;
		
		/**
		 * Creates the new builder.
		 * 
		 * @param dispatcher The dispatcher to use for any created HandoffConnectors.
		 */
		public Builder(Consumer<Runnable> dispatcher)
		{
			_dispatcher = dispatcher;
			_perUserConnectors = new HashMap<>();
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
		}
		
		/**
		 * Called once start-up has completed to finish the creation of the registry.
		 * Note that this renders the receiver unusable (as a way to catch bugs).
		 * 
		 * @param localUserKey The local user's key (must have been explicitly registered).
		 * @return The new registry.
		 */
		public EntryCacheRegistry buildRegistry(IpfsKey localUserKey)
		{
			Assert.assertTrue(!_done);
			// We set the _done flag just to avoid errors of the builder still being in use.
			// While it could be used to produce multiple registries, we don't use it that way so any further use is an error we want to catch.
			_done = true;
			return new EntryCacheRegistry(_dispatcher, localUserKey, _perUserConnectors);
		}
	}
}
