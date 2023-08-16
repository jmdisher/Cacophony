package com.jeffdisher.cacophony.caches;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.logic.HandoffConnector.IHandoffListener;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestEntryCacheRegistry
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] { 1 });
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] { 2 });
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] { 3 });

	@Test
	public void empty() throws Throwable
	{
		// Set everything up but add nothing.
		EntryCacheRegistry registry = new EntryCacheRegistry((Runnable run) -> run.run());
		registry.createHomeUser(MockKeys.K1);
		registry.initializeCombinedView();
		
		FakeListener listener = new FakeListener();
		FakeListener combined = new FakeListener();
		registry.getReadOnlyConnector(MockKeys.K1).registerListener(listener, 0);
		registry.getCombinedConnector().registerListener(combined, 0);
		Assert.assertEquals(0, listener.keysInOrder.size());
		Assert.assertEquals(0, combined.keysInOrder.size());
	}

	@Test
	public void basic() throws Throwable
	{
		// Just cover some basic cases and make sure the output makes sense.
		EntryCacheRegistry registry = new EntryCacheRegistry((Runnable run) -> run.run());
		registry.createHomeUser(MockKeys.K1);
		registry.createHomeUser(MockKeys.K2);
		registry.addLocalElement(MockKeys.K1, F1, 1L);
		registry.addLocalElement(MockKeys.K2, F1, 1L);
		registry.initializeCombinedView();
		
		FakeListener listener1 = new FakeListener();
		FakeListener listener2 = new FakeListener();
		FakeListener combined = new FakeListener();
		registry.getReadOnlyConnector(MockKeys.K1).registerListener(listener1, 0);
		registry.getReadOnlyConnector(MockKeys.K2).registerListener(listener2, 0);
		registry.getCombinedConnector().registerListener(combined, 0);
		Assert.assertEquals(1, listener1.keysInOrder.size());
		Assert.assertEquals(1, listener2.keysInOrder.size());
		Assert.assertEquals(1, combined.keysInOrder.size());
		registry.addFolloweeElement(MockKeys.K2, F2, 3L);
		Assert.assertEquals(1, listener1.keysInOrder.size());
		Assert.assertEquals(2, listener2.keysInOrder.size());
		Assert.assertEquals(2, combined.keysInOrder.size());
	}

	@Test
	public void fullPreload() throws Throwable
	{
		IpfsFile[] list = new IpfsFile[20];
		for (int i = 0; i < list.length; ++i)
		{
			list[i] = MockSingleNode.generateHash(new byte[] { (byte)i });
		}
		
		EntryCacheRegistry registry = new EntryCacheRegistry((Runnable run) -> run.run());
		registry.createHomeUser(MockKeys.K1);
		for (int i = 0; i < list.length; ++i)
		{
			registry.addLocalElement(MockKeys.K1, list[i], i);
		}
		registry.initializeCombinedView();
		
		FakeListener listener = new FakeListener();
		FakeListener combined = new FakeListener();
		registry.getReadOnlyConnector(MockKeys.K1).registerListener(listener, 0);
		registry.getCombinedConnector().registerListener(combined, 0);
		Assert.assertEquals(20, listener.keysInOrder.size());
		Assert.assertEquals(20, combined.keysInOrder.size());
		// Verify the first and last in the list.
		Assert.assertEquals(list[0], combined.keysInOrder.get(0));
		Assert.assertEquals(list[19], combined.keysInOrder.get(19));
	}

	@Test
	public void incrementalFunction() throws Throwable
	{
		// Show how the combination of multiple users works when built up incrementally.
		IpfsFile[] start = new IpfsFile[5];
		for (int i = 0; i < start.length; ++i)
		{
			start[i] = MockSingleNode.generateHash(new byte[] { (byte)i });
		}
		
		EntryCacheRegistry registry = new EntryCacheRegistry((Runnable run) -> run.run());
		registry.createHomeUser(MockKeys.K1);
		// Add some initial data to the local user.
		for (int i = 0; i < start.length; ++i)
		{
			registry.addLocalElement(MockKeys.K1, start[i], i);
		}
		registry.initializeCombinedView();
		
		// Now, synthesize a new followee.
		registry.createNewFollowee(MockKeys.K2);
		
		// Register all the listeners.
		FakeListener listener1 = new FakeListener();
		FakeListener listener2 = new FakeListener();
		FakeListener combined = new FakeListener();
		registry.getReadOnlyConnector(MockKeys.K1).registerListener(listener1, 0);
		registry.getReadOnlyConnector(MockKeys.K2).registerListener(listener2, 0);
		registry.getCombinedConnector().registerListener(combined, 0);
		
		// Add a bunch of data for the new followee (partial overlap with local).
		IpfsFile[] followeeAdded = new IpfsFile[5];
		for (int i = 0; i < followeeAdded.length; i ++)
		{
			followeeAdded[i] = MockSingleNode.generateHash(new byte[] { (byte)(i * 2) });
			registry.addFolloweeElement(MockKeys.K2, followeeAdded[i], i);
		}
		
		// Add some more data to the local user (partial overlap with followee).
		IpfsFile[] localAdded = new IpfsFile[5];
		for (int i = 0; i < localAdded.length; i ++)
		{
			localAdded[i] = MockSingleNode.generateHash(new byte[] { (byte)(i + start.length) });
			registry.addLocalElement(MockKeys.K1, localAdded[i], i);
		}
		
		Assert.assertEquals(10, listener1.keysInOrder.size());
		Assert.assertEquals(5, listener2.keysInOrder.size());
		Assert.assertEquals(10, combined.keysInOrder.size());
		Assert.assertEquals(start[0], combined.keysInOrder.get(0));
		Assert.assertEquals(start[1], combined.keysInOrder.get(1));
		Assert.assertEquals(start[2], combined.keysInOrder.get(2));
		Assert.assertEquals(start[3], combined.keysInOrder.get(3));
		Assert.assertEquals(start[4], combined.keysInOrder.get(4));
		Assert.assertEquals(followeeAdded[3], combined.keysInOrder.get(5));
		Assert.assertEquals(followeeAdded[4], combined.keysInOrder.get(6));
		Assert.assertEquals(localAdded[0], combined.keysInOrder.get(7));
		Assert.assertEquals(localAdded[2], combined.keysInOrder.get(8));
		Assert.assertEquals(localAdded[4], combined.keysInOrder.get(9));
	}

	@Test
	public void preDelete() throws Throwable
	{
		// Populate a bunch of entries from 2 users and delete them all before attaching to verify that nothing appears.
		EntryCacheRegistry registry = new EntryCacheRegistry((Runnable run) -> run.run());
		registry.createHomeUser(MockKeys.K1);
		registry.initializeCombinedView();
		
		// Now, synthesize a new followee.
		registry.createNewFollowee(MockKeys.K2);
		
		// Store all the data.
		IpfsFile[] local = new IpfsFile[5];
		IpfsFile[] followee = new IpfsFile[5];
		for (int i = 0; i < local.length; ++i)
		{
			local[i] = MockSingleNode.generateHash(new byte[] { (byte)i });
			registry.addLocalElement(MockKeys.K1, local[i], 2 * i);
			followee[i] = MockSingleNode.generateHash(new byte[] { (byte)(i * 2) });
			registry.addFolloweeElement(MockKeys.K2, followee[i], 2 * i + 1);
		}
		
		// Now, delete everything.
		for (int i = 0; i < local.length; ++i)
		{
			registry.removeLocalElement(MockKeys.K1, local[i]);
			registry.removeFolloweeElement(MockKeys.K2, followee[i]);
		}
		
		// Register all the listeners.
		FakeListener listener1 = new FakeListener();
		FakeListener listener2 = new FakeListener();
		FakeListener combined = new FakeListener();
		registry.getReadOnlyConnector(MockKeys.K1).registerListener(listener1, 0);
		registry.getReadOnlyConnector(MockKeys.K2).registerListener(listener2, 0);
		registry.getCombinedConnector().registerListener(combined, 0);
		
		// Verify that these are all empty.
		Assert.assertEquals(0, listener1.keysInOrder.size());
		Assert.assertEquals(0, listener2.keysInOrder.size());
		Assert.assertEquals(0, combined.keysInOrder.size());
	}

	@Test
	public void postDelete() throws Throwable
	{
		// Populate a bunch of entries from 2 users, attach the listeners, then delete them all and verify that the list is now empty.
		EntryCacheRegistry registry = new EntryCacheRegistry((Runnable run) -> run.run());
		registry.createHomeUser(MockKeys.K1);
		registry.initializeCombinedView();
		
		// Now, synthesize a new followee.
		registry.createNewFollowee(MockKeys.K2);
		
		// Store all the data.
		IpfsFile[] local = new IpfsFile[5];
		IpfsFile[] followee = new IpfsFile[5];
		for (int i = 0; i < local.length; ++i)
		{
			local[i] = MockSingleNode.generateHash(new byte[] { (byte)i });
			registry.addLocalElement(MockKeys.K1, local[i], 2 * i);
			followee[i] = MockSingleNode.generateHash(new byte[] { (byte)(i * 2) });
			registry.addFolloweeElement(MockKeys.K2, followee[i], 2 * i + 1);
		}
		
		// Register all the listeners.
		FakeListener listener1 = new FakeListener();
		FakeListener listener2 = new FakeListener();
		FakeListener combined = new FakeListener();
		registry.getReadOnlyConnector(MockKeys.K1).registerListener(listener1, 0);
		registry.getReadOnlyConnector(MockKeys.K2).registerListener(listener2, 0);
		registry.getCombinedConnector().registerListener(combined, 0);
		
		// Now, delete everything.
		for (int i = 0; i < local.length; ++i)
		{
			registry.removeLocalElement(MockKeys.K1, local[i]);
			registry.removeFolloweeElement(MockKeys.K2, followee[i]);
		}
		
		// Verify that these are all empty but we saw the deletes.
		Assert.assertEquals(0, listener1.keysInOrder.size());
		Assert.assertEquals(5, listener1.deleteCount);
		Assert.assertEquals(0, listener2.keysInOrder.size());
		Assert.assertEquals(5, listener2.deleteCount);
		Assert.assertEquals(0, combined.keysInOrder.size());
		Assert.assertEquals(7, combined.deleteCount);
	}

	@Test
	public void fullRefcountDelete() throws Throwable
	{
		IpfsFile[] list = new IpfsFile[20];
		for (int i = 0; i < list.length; ++i)
		{
			list[i] = MockSingleNode.generateHash(new byte[] { (byte)i });
		}
		
		EntryCacheRegistry registry = new EntryCacheRegistry((Runnable run) -> run.run());
		registry.createHomeUser(MockKeys.K1);
		for (int i = 0; i < list.length; ++i)
		{
			registry.addLocalElement(MockKeys.K1, list[i], i);
		}
		registry.createHomeUser(MockKeys.K2);
		for (int i = 0; i < 5; ++i)
		{
			registry.addFolloweeElement(MockKeys.K2, list[i], list.length + i);
		}
		registry.initializeCombinedView();
		registry.addFolloweeElement(MockKeys.K2, list[5], list.length + 5);
		
		FakeListener local1 = new FakeListener();
		FakeListener followee1 = new FakeListener();
		FakeListener combined1 = new FakeListener();
		registry.getReadOnlyConnector(MockKeys.K1).registerListener(local1, 0);
		registry.getReadOnlyConnector(MockKeys.K2).registerListener(followee1, 0);
		registry.getCombinedConnector().registerListener(combined1, 0);
		Assert.assertEquals(6, followee1.keysInOrder.size());
		Assert.assertEquals(20, local1.keysInOrder.size());
		Assert.assertEquals(20, combined1.keysInOrder.size());
		registry.getReadOnlyConnector(MockKeys.K1).unregisterListener(local1);
		registry.getReadOnlyConnector(MockKeys.K2).unregisterListener(followee1);
		registry.getCombinedConnector().unregisterListener(combined1);
		
		// Now, remove everything.
		for (int i = 0; i < list.length; ++i)
		{
			registry.removeLocalElement(MockKeys.K1, list[i]);
			if (i < 6)
			{
				registry.removeFolloweeElement(MockKeys.K2, list[i]);
			}
		}
		registry.removeFollowee(MockKeys.K2);
		
		FakeListener local2 = new FakeListener();
		FakeListener combined2 = new FakeListener();
		registry.getReadOnlyConnector(MockKeys.K1).registerListener(local2, 0);
		registry.getCombinedConnector().registerListener(combined2, 0);
		Assert.assertEquals(0, local2.keysInOrder.size());
		Assert.assertEquals(0, combined2.keysInOrder.size());
	}

	@Test
	public void interleavedBeforeAfterBootstrap() throws Throwable
	{
		// Shows that interleaved timestamps are put in order, before bootstrap, but we only use call order, after.
		// Show how the combination of multiple users works when built up incrementally.
		EntryCacheRegistry registry = new EntryCacheRegistry((Runnable run) -> run.run());
		registry.createHomeUser(MockKeys.K1);
		registry.createNewFollowee(MockKeys.K2);
		
		IpfsFile[] startHome = new IpfsFile[2];
		for (int i = 0; i < startHome.length; ++i)
		{
			startHome[i] = MockSingleNode.generateHash(new byte[] { (byte)i });
			registry.addLocalElement(MockKeys.K1, startHome[i], 2 * i);
		}
		IpfsFile[] startFollowee = new IpfsFile[2];
		for (int i = 0; i < startFollowee.length; ++i)
		{
			startFollowee[i] = MockSingleNode.generateHash(new byte[] { (byte)(i + 2) });
			registry.addFolloweeElement(MockKeys.K2, startFollowee[i], 2 * i + 1);
		}
		registry.initializeCombinedView();
		
		IpfsFile[] afterHome = new IpfsFile[2];
		for (int i = 0; i < afterHome.length; ++i)
		{
			afterHome[i] = MockSingleNode.generateHash(new byte[] { (byte)(i + 4) });
			registry.addLocalElement(MockKeys.K1, afterHome[i], 2 * i + 4);
		}
		IpfsFile[] afterFollowee = new IpfsFile[2];
		for (int i = 0; i < afterFollowee.length; ++i)
		{
			afterFollowee[i] = MockSingleNode.generateHash(new byte[] { (byte)(i + 6) });
			registry.addFolloweeElement(MockKeys.K2, afterFollowee[i], 2 * i + 5);
		}
		
		// Verify the order in the combined view.
		FakeListener combined = new FakeListener();
		registry.getCombinedConnector().registerListener(combined, 0);
		
		Assert.assertEquals(8, combined.keysInOrder.size());
		Assert.assertEquals(startHome[0], combined.keysInOrder.get(0));
		Assert.assertEquals(startFollowee[0], combined.keysInOrder.get(1));
		Assert.assertEquals(startHome[1], combined.keysInOrder.get(2));
		Assert.assertEquals(startFollowee[1], combined.keysInOrder.get(3));
		Assert.assertEquals(afterHome[0], combined.keysInOrder.get(4));
		Assert.assertEquals(afterHome[1], combined.keysInOrder.get(5));
		Assert.assertEquals(afterFollowee[0], combined.keysInOrder.get(6));
		Assert.assertEquals(afterFollowee[1], combined.keysInOrder.get(7));
	}


	private static class FakeListener implements IHandoffListener<IpfsFile, Void>
	{
		// These are in order such that the oldest is first, newest at the end.
		public final List<IpfsFile> keysInOrder = new ArrayList<>();
		public int deleteCount;
		@Override
		public boolean create(IpfsFile key, Void value, boolean isNewest)
		{
			Assert.assertFalse(this.keysInOrder.contains(key));
			if (isNewest)
			{
				this.keysInOrder.add(key);
			}
			else
			{
				this.keysInOrder.add(0, key);
			}
			return true;
		}
		@Override
		public boolean update(IpfsFile key, Void value)
		{
			throw new AssertionError("Not Called");
		}
		@Override
		public boolean destroy(IpfsFile key)
		{
			this.keysInOrder.remove(key);
			this.deleteCount += 1;
			return true;
		}
		@Override
		public boolean specialChanged(String special)
		{
			throw new AssertionError("Not Called");
		}
	}
}
