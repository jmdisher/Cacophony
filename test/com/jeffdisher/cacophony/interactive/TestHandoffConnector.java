package com.jeffdisher.cacophony.interactive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.logic.HandoffConnector;


public class TestHandoffConnector
{
	// The dispatcher is expected to lock-step execution, so we synchronize the call as a simple approach.
	private static final Consumer<Runnable> DISPATCHER = new Consumer<>() {
		@Override
		public void accept(Runnable arg0)
		{
			synchronized (this)
			{
				arg0.run();
			}
		}
	};

	@Test
	public void testNoListener() throws Throwable
	{
		HandoffConnector<String, String> connector = new HandoffConnector<>(DISPATCHER);
		connector.create("one", "1 1");
		connector.update("one", "1 2");
		connector.create("two", "2 1");
		connector.destroy("one");
	}

	@Test
	public void testOneListener() throws Throwable
	{
		HandoffConnector<String, String> connector = new HandoffConnector<>(DISPATCHER);
		TestListener listen1 = new TestListener();
		connector.registerListener(listen1, 0);
		connector.create("one", "1 1");
		connector.update("one", "1 2");
		connector.create("two", "2 1");
		connector.destroy("one");
		
		Assert.assertEquals(1, listen1.map.size());
		Assert.assertEquals("2 1", listen1.map.get("two"));
		Assert.assertEquals("two", listen1.keyOrder.get(0));
	}

	@Test
	public void testOneListenerAddRemove() throws Throwable
	{
		HandoffConnector<String, String> connector = new HandoffConnector<>(DISPATCHER);
		TestListener listen1 = new TestListener();
		connector.create("one", "1 1");
		connector.registerListener(listen1, 0);
		connector.update("one", "1 2");
		connector.create("two", "2 1");
		connector.unregisterListener(listen1);
		connector.destroy("one");
		Assert.assertEquals(2, listen1.map.size());
		Assert.assertEquals("1 2", listen1.map.get("one"));
		Assert.assertEquals("2 1", listen1.map.get("two"));
		Assert.assertEquals("one", listen1.keyOrder.get(0));
		Assert.assertEquals("two", listen1.keyOrder.get(1));
	}

	@Test
	public void testTwoListenersOneConstant() throws Throwable
	{
		HandoffConnector<String, String> connector = new HandoffConnector<>(DISPATCHER);
		TestListener listen1 = new TestListener();
		TestListener listen2 = new TestListener();
		connector.registerListener(listen1, 0);
		connector.create("one", "1 1");
		connector.registerListener(listen2, 0);
		connector.update("one", "1 2");
		connector.create("two", "2 1");
		connector.unregisterListener(listen2);
		connector.destroy("one");
		
		Assert.assertEquals(1, listen1.map.size());
		Assert.assertEquals("2 1", listen1.map.get("two"));
		Assert.assertEquals("two", listen1.keyOrder.get(0));
		
		Assert.assertEquals(2, listen2.map.size());
		Assert.assertEquals("1 2", listen2.map.get("one"));
		Assert.assertEquals("2 1", listen2.map.get("two"));
		Assert.assertEquals("one", listen2.keyOrder.get(0));
		Assert.assertEquals("two", listen2.keyOrder.get(1));
	}

	@Test
	public void testOneListenerForceDisconect() throws Throwable
	{
		HandoffConnector<String, String> connector = new HandoffConnector<>(DISPATCHER);
		TestListener listen1 = new TestListener();
		connector.registerListener(listen1, 0);
		connector.create("one", "1 1");
		connector.update("one", "1 2");
		listen1.fail = true;
		connector.create("two", "2 1");
		connector.destroy("one");
		
		Assert.assertEquals(1, listen1.map.size());
		Assert.assertEquals("1 2", listen1.map.get("one"));
		Assert.assertEquals("one", listen1.keyOrder.get(0));
	}

	@Test
	public void testOrderCheck() throws Throwable
	{
		HandoffConnector<String, String> connector = new HandoffConnector<>(DISPATCHER);
		TestListener listen1 = new TestListener();
		TestListener listen2 = new TestListener();
		connector.registerListener(listen1, 0);
		connector.create("one", "1 1");
		connector.create("two", "2 1");
		connector.create("three", "3 1");
		connector.registerListener(listen2, 0);
		connector.update("two", "2 2");
		connector.update("one", "1 2");
		connector.unregisterListener(listen1);
		connector.create("four", "4 1");
		
		Assert.assertEquals(3, listen1.keyOrder.size());
		Assert.assertEquals("one", listen1.keyOrder.get(0));
		Assert.assertEquals("two", listen1.keyOrder.get(1));
		Assert.assertEquals("three", listen1.keyOrder.get(2));
		
		Assert.assertEquals(4, listen2.keyOrder.size());
		Assert.assertEquals("one", listen2.keyOrder.get(0));
		Assert.assertEquals("two", listen2.keyOrder.get(1));
		Assert.assertEquals("three", listen2.keyOrder.get(2));
		Assert.assertEquals("four", listen2.keyOrder.get(3));
	}

	@Test
	public void testNullKeys() throws Throwable
	{
		HandoffConnector<String, Void> connector = new HandoffConnector<>(DISPATCHER);
		connector.create("one", null);
		connector.update("one", null);
		connector.destroy("one");
	}

	@Test
	public void testSpecial() throws Throwable
	{
		HandoffConnector<String, String> connector = new HandoffConnector<>(DISPATCHER);
		TestListener listen1 = new TestListener();
		TestListener listen2 = new TestListener();
		connector.registerListener(listen1, 0);
		connector.create("one", "1 1");
		connector.create("two", "2 1");
		connector.setSpecial("special");
		// This redundant update should not appear.
		connector.setSpecial("special");
		connector.registerListener(listen2, 0);
		connector.update("two", "2 2");
		connector.unregisterListener(listen1);
		connector.setSpecial(null);
		
		Assert.assertEquals(2, listen1.keyOrder.size());
		Assert.assertEquals("one", listen1.keyOrder.get(0));
		Assert.assertEquals("two", listen1.keyOrder.get(1));
		Assert.assertEquals("special", listen1.special);
		
		Assert.assertEquals(2, listen2.keyOrder.size());
		Assert.assertEquals("one", listen2.keyOrder.get(0));
		Assert.assertEquals("two", listen2.keyOrder.get(1));
		Assert.assertNull(listen2.special);
	}

	@Test
	public void testBidirectionOrder() throws Throwable
	{
		HandoffConnector<String, String> connector = new HandoffConnector<>(DISPATCHER);
		TestListener listen1 = new TestListener();
		TestListener listen2 = new TestListener();
		connector.registerListener(listen1, 3);
		connector.create("one", "1 1");
		connector.create("two", "2 1");
		connector.create("three", "3 1");
		connector.create("four", "4 1");
		connector.create("five", "5 1");
		connector.registerListener(listen2, 3);
		connector.update("two", "2 2");
		connector.update("one", "1 2");
		connector.create("six", "6 1");
		connector.unregisterListener(listen1);
		
		Assert.assertEquals(6, listen1.keyOrder.size());
		Assert.assertEquals("one", listen1.keyOrder.get(0));
		Assert.assertEquals("two", listen1.keyOrder.get(1));
		Assert.assertEquals("three", listen1.keyOrder.get(2));
		Assert.assertEquals("four", listen1.keyOrder.get(3));
		Assert.assertEquals("five", listen1.keyOrder.get(4));
		Assert.assertEquals("six", listen1.keyOrder.get(5));
		Assert.assertEquals("1 2", listen1.map.get("one"));
		
		Assert.assertEquals(4, listen2.keyOrder.size());
		Assert.assertEquals("three", listen2.keyOrder.get(0));
		Assert.assertEquals("four", listen2.keyOrder.get(1));
		Assert.assertEquals("five", listen2.keyOrder.get(2));
		Assert.assertEquals("six", listen2.keyOrder.get(3));
	}

	@Test
	public void testScrollBack() throws Throwable
	{
		HandoffConnector<String, String> connector = new HandoffConnector<>(DISPATCHER);
		TestListener listen1 = new TestListener();
		connector.create("one", "1 1");
		connector.create("two", "2 1");
		connector.create("three", "3 1");
		connector.create("four", "4 1");
		connector.create("five", "5 1");
		connector.registerListener(listen1, 2);
		Assert.assertEquals(2, listen1.keyOrder.size());
		Assert.assertEquals("four", listen1.keyOrder.get(0));
		Assert.assertEquals("five", listen1.keyOrder.get(1));
		
		connector.destroy("four");
		Assert.assertEquals(1, listen1.keyOrder.size());
		connector.update("one", "1 2");
		connector.create("six", "6 1");
		Assert.assertEquals(2, listen1.keyOrder.size());
		
		connector.requestOlderElements(listen1, 2);
		Assert.assertEquals(4, listen1.keyOrder.size());
		Assert.assertEquals("two", listen1.keyOrder.get(0));
		Assert.assertEquals("three", listen1.keyOrder.get(1));
		Assert.assertEquals("five", listen1.keyOrder.get(2));
		Assert.assertEquals("six", listen1.keyOrder.get(3));
		
		connector.unregisterListener(listen1);
	}


	private static class TestListener implements HandoffConnector.IHandoffListener<String, String>
	{
		public Map<String, String> map = new HashMap<>();
		public List<String> keyOrder = new ArrayList<>();
		public String special = null;
		public boolean fail = false;
		
		@Override
		public boolean create(String key, String value, boolean isNewest)
		{
			if (!fail)
			{
				String old = map.put(key, value);
				Assert.assertNull(old);
				if (isNewest)
				{
					keyOrder.add(key);
				}
				else
				{
					keyOrder.add(0, key);
				}
			}
			return !fail;
		}
		@Override
		public boolean update(String key, String value)
		{
			if (!fail)
			{
				String old = map.put(key, value);
				Assert.assertNotNull(old);
			}
			return !fail;
		}
		@Override
		public boolean destroy(String key)
		{
			if (!fail)
			{
				String old = map.remove(key);
				Assert.assertNotNull(old);
				boolean didRemove = keyOrder.remove(key);
				Assert.assertTrue(didRemove);
			}
			return !fail;
		}
		@Override
		public boolean specialChanged(String special)
		{
			Assert.assertNotEquals(this.special, special);
			this.special = special;
			return !fail;
		}
	}
}
