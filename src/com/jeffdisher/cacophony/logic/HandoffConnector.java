package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
	private final IdentityHashMap<IHandoffListener<K, V>, ListenerState<K, V>> _listeners;
	// The cache of data.
	private final Map<K, V> _cache;
	// The order in which the keys were created (since some representations want to preserve order).
	private final List<K> _order;
	// A "special" string can be set in the connector for relaying out-of-band meta-data, but this is kept very simple.
	private String _special;

	/**
	 * Creates a new connector, executing all of its state mutations through the given dispatcher.
	 * 
	 * @param dispatcher Responsible for running the actions applied to this connector.  Must serialize all runnables
	 * from a given connector.
	 */
	public HandoffConnector(Consumer<Runnable> dispatcher)
	{
		_dispatcher = dispatcher;
		_listeners = new IdentityHashMap<>();
		_cache = new HashMap<>();
		_order = new ArrayList<>();
	}

	/**
	 * Adds a listener to the internal listener set.  The listener MUST NOT already be in the set.
	 * 
	 * @param listener The listener to add.
	 * @param limit If greater than 0, the events will be sent from newest to oldest, only up to this many.
	 */
	public void registerListener(IHandoffListener<K, V> listener, int limit)
	{
		_dispatcher.accept(() ->
		{
			// Add to the set.
			// (do a quick check that there are no duplicates).
			Assert.assertTrue(!_listeners.containsKey(listener));
			
			// Set the special, if not null.
			if (null != _special)
			{
				listener.specialChanged(_special);
			}
			
			ListenerState<K, V> state = new ListenerState<>(listener);
			_listeners.put(listener, state);
			if (limit > 0)
			{
				List<K> reversed = new ArrayList<>(_order);
				Collections.reverse(reversed);
				int elementsSent = 0;
				for (K key : reversed)
				{
					V value = _cache.get(key);
					listener.create(key, value, false);
					state.keysSent.add(key);
					state.oldestKeySent = key;
					elementsSent += 1;
					if (elementsSent == limit)
					{
						break;
					}
				}
			}
			else
			{
				// We want everything so just walk the list, in-order.
				if (!_order.isEmpty())
				{
					state.oldestKeySent = _order.get(0);
					for (K key : _order)
					{
						V value = _cache.get(key);
						listener.create(key, value, true);
						state.keysSent.add(key);
					}
				}
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
			Assert.assertTrue(null != _listeners.remove(listener));
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
			// No over-write in this path.
			Assert.assertTrue(!_cache.containsKey(key));
			_cache.put(key, value);
			_order.add(key);
			
			// Tell everyone.
			Iterator<ListenerState<K, V>> iter = _listeners.values().iterator();
			while (iter.hasNext())
			{
				ListenerState<K, V> listenerState = iter.next();
				boolean ok = listenerState.listener.create(key, value, true);
				if (ok)
				{
					// Record that this listener knows about this key.
					listenerState.keysSent.add(key);
				}
				else
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
			// We expect the element to be in the map (but it may have a null value).
			Assert.assertTrue(_cache.containsKey(key));
			_cache.put(key, value);
			
			// Tell everyone.
			Iterator<ListenerState<K, V>> iter = _listeners.values().iterator();
			while (iter.hasNext())
			{
				ListenerState<K, V> listenerState = iter.next();
				// We only want to send this if this listener has actually seen this create.
				if (listenerState.keysSent.contains(key))
				{
					boolean ok = listenerState.listener.update(key, value);
					if (!ok)
					{
						iter.remove();
					}
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
			// We expect the element to be in the map (but it may have a null value).
			Assert.assertTrue(_cache.containsKey(key));
			_cache.remove(key);
			// Capture the more recent key than this one, in case any listeners need their oldest key bumped up.
			int indexToRemove = _order.indexOf(key);
			K moreRecentKey = ((indexToRemove + 1) < _order.size())
					? _order.get(indexToRemove + 1)
					: null
			;
			boolean didRemove = (null != _order.remove(indexToRemove));
			// Must remove.
			Assert.assertTrue(didRemove);
			
			// Tell everyone.
			Iterator<ListenerState<K, V>> iter = _listeners.values().iterator();
			
			while (iter.hasNext())
			{
				ListenerState<K, V> listenerState = iter.next();
				// We only want to send this if this listener has actually seen this create.
				if (listenerState.keysSent.contains(key))
				{
					boolean ok = listenerState.listener.destroy(key);
					if (!ok)
					{
						iter.remove();
					}
					// We also need to update the state of the listener's keys.
					listenerState.keysSent.remove(key);
					if (key.equals(listenerState.oldestKeySent))
					{
						listenerState.oldestKeySent = moreRecentKey;
					}
				}
			}
		});
	}

	public void setSpecial(String special)
	{
		_dispatcher.accept(() ->
		{
			// Just set it, if it changed.
			boolean isSame = (_special == special) || ((null != _special) && _special.equals(special));
			if (!isSame)
			{
				_special = special;
				
				// Tell everyone.
				Iterator<IHandoffListener<K, V>> iter = _listeners.keySet().iterator();
				while (iter.hasNext())
				{
					IHandoffListener<K, V> listener = iter.next();
					boolean ok = listener.specialChanged(_special);
					if (!ok)
					{
						iter.remove();
					}
				}
			}
		});
	}

	/**
	 * Walks backward through the list of in-order keys, sending the create calls for up to the next elementToRequest
	 * keys preceding the last one sent.
	 * 
	 * @param listener The listener to notify.
	 * @param elementsToRequest The maximum number of creates to send.
	 */
	public void requestOlderElements(IHandoffListener<K, V> listener, int elementsToRequest)
	{
		Assert.assertTrue(elementsToRequest > 0);
		_dispatcher.accept(() ->
		{
			ListenerState<K, V> state = _listeners.get(listener);
			List<K> reversed = new ArrayList<>(_order);
			Collections.reverse(reversed);
			
			boolean didFind = (null == state.oldestKeySent);
			int elementsSent = 0;
			for (K key : reversed)
			{
				if (didFind)
				{
					V value = _cache.get(key);
					listener.create(key, value, false);
					elementsSent += 1;
					
					// Update state.
					state.keysSent.add(key);
					state.oldestKeySent = key;
					
					if (elementsSent == elementsToRequest)
					{
						break;
					}
				}
				else
				{
					didFind = state.oldestKeySent.equals(key);
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
		 * @param isNewest True if this entry should be considered the most recent (otherwise, consider it the oldest).
		 * @return True if this was successful or false to unregister the target.
		 */
		boolean create(K key, V value, boolean isNewest);
		
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
		
		/**
		 * Called when the special out-of-band string is changed.
		 * 
		 * @param special The new value of the special.
		 * @return True if this was successful or false to unregister the target.
		 */
		boolean specialChanged(String special);
	}


	private static class ListenerState<K, V>
	{
		public final IHandoffListener<K, V> listener;
		public final Set<K> keysSent;
		public K oldestKeySent;
		
		public ListenerState(IHandoffListener<K, V> listener)
		{
			this.listener = listener;
			this.keysSent = new HashSet<>();
		}
	}
}
