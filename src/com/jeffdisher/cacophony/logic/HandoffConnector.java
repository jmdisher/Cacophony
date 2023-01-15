package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.utils.Assert;


/**
 * The generic adapter which allows any number of listeners to listen for update events and connect/disconnect at any
 * time.  The general use-case for this is web socket listeners to relay information to the front-end and may connect/
 * disconnect at any time and in any number.
 * Note that the actual calls to mutate internal state, as well as call out to listeners, are made via the given
 * dispatcher.  While this dispatcher has a great deal of freedom in its implementation (inline, another thread, etc),
 * it is required that it serializes all calls to Runnables from the same HandoffConnector.
 * The general idea is that all data change events can be reduced to a create/update/delete operation so we can cache
 * these simple projections of arbitrary data here.
 */
public class HandoffConnector<K, V>
{
	private final Consumer<Runnable> _dispatcher;

	// NOTE:  We only interact with the instance variables in the dispatcher's thread!
	// The listeners which are attached to us - we will just use a HashSet, although technically this should be identity-based.
	private final Set<IHandoffListener<K, V>> _listeners;
	// The cache of data.
	private final Map<K, V> _cache;
	// The order in which the keys were created (since some representations want to preserve order).
	private final List<K> _order;

	/**
	 * Creates a new connector, executing all of its state mutations through the given dispatcher.
	 * 
	 * @param dispatcher Responsible for running the actions applied to this connector.  Must serialize all runnables
	 * from a given connector.
	 */
	public HandoffConnector(Consumer<Runnable> dispatcher)
	{
		_dispatcher = dispatcher;
		_listeners = new HashSet<>();
		_cache = new HashMap<>();
		_order = new ArrayList<>();
	}

	/**
	 * Adds a listener to the internal listener set.  The listener MUST NOT already be in the set.
	 * 
	 * @param listener The listener to add.
	 */
	public void registerListener(IHandoffListener<K, V> listener)
	{
		_dispatcher.accept(() ->
		{
			// Add to the set.
			boolean didAdd = _listeners.add(listener);
			Assert.assertTrue(didAdd);
			
			// Walk the list and relay the data we already have, in-order.
			for (K key : _order)
			{
				V value = _cache.get(key);
				listener.create(key, value);
			}
		});
	}

	/**
	 * Removes a listener from the internal listener set.  The listener MUST be in the set.
	 * 
	 * @param listener The listener to remove.
	 */
	public void unregisterListener(IHandoffListener<K, V> listener)
	{
		_dispatcher.accept(() ->
		{
			boolean didRemove = _listeners.remove(listener);
			Assert.assertTrue(didRemove);
		});
	}

	/**
	 * A Create CRUD operation to define a new key-value pair.  The key MUST NOT have already been created.
	 * 
	 * @param key The key to create (MUST NOT already exist).
	 * @param value The initial value for the key.
	 */
	public void create(K key, V value)
	{
		_dispatcher.accept(() ->
		{
			V old = _cache.put(key, value);
			// No over-write in this path.
			Assert.assertTrue(null == old);
			_order.add(key);
			
			// Tell everyone.
			Iterator<IHandoffListener<K, V>> iter = _listeners.iterator();
			while (iter.hasNext())
			{
				IHandoffListener<K, V> listener = iter.next();
				boolean ok = listener.create(key, value);
				if (!ok)
				{
					iter.remove();
				}
			}
		});
	}

	/**
	 * A Update CRUD operation to modify an existing key-value pair.  The key MUST have already been created.
	 * 
	 * @param key The key to update (MUST already exist).
	 * @param value The new value for the key.
	 */
	public void update(K key, V value)
	{
		_dispatcher.accept(() ->
		{
			V old = _cache.put(key, value);
			// Must over-write in this path.
			Assert.assertTrue(null != old);
			
			// Tell everyone.
			Iterator<IHandoffListener<K, V>> iter = _listeners.iterator();
			while (iter.hasNext())
			{
				IHandoffListener<K, V> listener = iter.next();
				boolean ok = listener.update(key, value);
				if (!ok)
				{
					iter.remove();
				}
			}
		});
	}

	/**
	 * A Destroy CRUD operation to remove an existing key.  The key MUST have already been created.
	 * 
	 * @param key The key to remove (MUST already exist).
	 */
	public void destroy(K key)
	{
		_dispatcher.accept(() ->
		{
			V old = _cache.remove(key);
			// Must remove.
			Assert.assertTrue(null != old);
			boolean didRemove = _order.remove(key);
			// Must remove.
			Assert.assertTrue(didRemove);
			
			// Tell everyone.
			Iterator<IHandoffListener<K, V>> iter = _listeners.iterator();
			while (iter.hasNext())
			{
				IHandoffListener<K, V> listener = iter.next();
				boolean ok = listener.destroy(key);
				if (!ok)
				{
					iter.remove();
				}
			}
		});
	}


	/**
	 * The general callback interface for listeners.
	 * The methods are called via the dispatcher.
	 * 
	 * @param <K> The key type.
	 * @param <V> The value type.
	 */
	public static interface IHandoffListener<K, V>
	{
		/**
		 * Called when a key is set for the first time.
		 * 
		 * @param key The key being created.
		 * @param value The initial value.
		 * @return True if this was successful or false to unregister the target.
		 */
		boolean create(K key, V value);
		
		/**
		 * Called when a key is modified after creation.
		 * 
		 * @param key The key being modified.
		 * @param value The new value.
		 * @return True if this was successful or false to unregister the target.
		 */
		boolean update(K key, V value);
		
		/**
		 * Called when a key is destroyed.
		 * 
		 * @param key The key being destroyed.
		 * @return True if this was successful or false to unregister the target.
		 */
		boolean destroy(K key);
	}
}