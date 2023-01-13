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
	private static final Consumer<Runnable> DISPATCHER = (Runnable task) -> task.run();

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
		connector.registerListener(listen1);
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
		connector.registerListener(listen1);
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
		connector.registerListener(listen1);
		connector.create("one", "1 1");
		connector.registerListener(listen2);
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
		connector.registerListener(listen1);
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
		connector.registerListener(listen1);
		connector.create("one", "1 1");
		connector.create("two", "2 1");
		connector.create("three", "3 1");
		connector.registerListener(listen2);
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


	private static class TestListener implements HandoffConnector.IHandoffListener<String, String>
	{
		public Map<String, String> map = new HashMap<>();
		public List<String> keyOrder = new ArrayList<>();
		public boolean fail = false;
		
		@Override
		public boolean create(String key, String value)
		{
			if (!fail)
			{
				String old = map.put(key, value);
				Assert.assertNull(old);
				keyOrder.add(key);
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
	}
}
