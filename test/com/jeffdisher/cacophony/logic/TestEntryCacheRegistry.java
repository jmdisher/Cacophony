package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.logic.HandoffConnector.IHandoffListener;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestEntryCacheRegistry
{
	public static final IpfsKey K1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	public static final IpfsKey K2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14W");
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] { 1 });
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] { 2 });
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] { 3 });

	@Test
	public void empty() throws Throwable
	{
		// Set everything up but add nothing.
		EntryCacheRegistry.Builder builder = new EntryCacheRegistry.Builder((Runnable run) -> run.run());
		builder.createConnector(K1);
		EntryCacheRegistry registry = builder.buildRegistry(K1);
		
		FakeListener listener = new FakeListener();
		registry.getReadOnlyConnector(K1).registerListener(listener, 0);
		Assert.assertEquals(0, listener.keysInOrder.size());
	}

	@Test
	public void basic() throws Throwable
	{
		// Just cover some basic cases and make sure the output makes sense.
		EntryCacheRegistry.Builder builder = new EntryCacheRegistry.Builder((Runnable run) -> run.run());
		builder.createConnector(K1);
		builder.createConnector(K2);
		builder.addToUser(K1, F1);
		builder.addToUser(K2, F1);
		EntryCacheRegistry registry = builder.buildRegistry(K1);
		
		FakeListener listener1 = new FakeListener();
		FakeListener listener2 = new FakeListener();
		registry.getReadOnlyConnector(K1).registerListener(listener1, 0);
		registry.getReadOnlyConnector(K2).registerListener(listener2, 0);
		Assert.assertEquals(1, listener1.keysInOrder.size());
		Assert.assertEquals(1, listener2.keysInOrder.size());
		registry.addFolloweeElement(K2, F2);
		Assert.assertEquals(1, listener1.keysInOrder.size());
		Assert.assertEquals(2, listener2.keysInOrder.size());
	}

	@Test
	public void fullPreload() throws Throwable
	{
		IpfsFile[] list = new IpfsFile[20];
		for (int i = 0; i < list.length; ++i)
		{
			list[i] = MockSingleNode.generateHash(new byte[] { (byte)i });
		}
		
		EntryCacheRegistry.Builder builder = new EntryCacheRegistry.Builder((Runnable run) -> run.run());
		builder.createConnector(K1);
		for (int i = 0; i < list.length; ++i)
		{
			builder.addToUser(K1, list[i]);
		}
		EntryCacheRegistry registry = builder.buildRegistry(K1);
		
		FakeListener listener = new FakeListener();
		registry.getReadOnlyConnector(K1).registerListener(listener, 0);
		Assert.assertEquals(20, listener.keysInOrder.size());
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
		
		EntryCacheRegistry.Builder builder = new EntryCacheRegistry.Builder((Runnable run) -> run.run());
		builder.createConnector(K1);
		// Add some initial data to the local user.
		for (int i = 0; i < start.length; ++i)
		{
			builder.addToUser(K1, start[i]);
		}
		EntryCacheRegistry registry = builder.buildRegistry(K1);
		
		// Now, synthesize a new followee.
		registry.createNewFollowee(K2);
		
		// Register all the listeners.
		FakeListener listener1 = new FakeListener();
		FakeListener listener2 = new FakeListener();
		registry.getReadOnlyConnector(K1).registerListener(listener1, 0);
		registry.getReadOnlyConnector(K2).registerListener(listener2, 0);
		
		// Add a bunch of data for the new followee (partial overlap with local).
		IpfsFile[] followeeAdded = new IpfsFile[5];
		for (int i = 0; i < start.length; i ++)
		{
			followeeAdded[i] = MockSingleNode.generateHash(new byte[] { (byte)(i * 2) });
			registry.addFolloweeElement(K2, followeeAdded[i]);
		}
		
		// Add some more data to the local user (partial overlap with followee).
		IpfsFile[] localAdded = new IpfsFile[5];
		for (int i = 0; i < start.length; i ++)
		{
			localAdded[i] = MockSingleNode.generateHash(new byte[] { (byte)(i + start.length) });
			registry.addLocalElement(localAdded[i]);
		}
		
		Assert.assertEquals(10, listener1.keysInOrder.size());
		Assert.assertEquals(5, listener2.keysInOrder.size());
	}

	@Test
	public void preDelete() throws Throwable
	{
		// Populate a bunch of entries from 2 users and delete them all before attaching to verify that nothing appears.
		EntryCacheRegistry.Builder builder = new EntryCacheRegistry.Builder((Runnable run) -> run.run());
		builder.createConnector(K1);
		EntryCacheRegistry registry = builder.buildRegistry(K1);
		
		// Now, synthesize a new followee.
		registry.createNewFollowee(K2);
		
		// Store all the data.
		IpfsFile[] local = new IpfsFile[5];
		IpfsFile[] followee = new IpfsFile[5];
		for (int i = 0; i < local.length; ++i)
		{
			local[i] = MockSingleNode.generateHash(new byte[] { (byte)i });
			registry.addLocalElement(local[i]);
			followee[i] = MockSingleNode.generateHash(new byte[] { (byte)(i * 2) });
			registry.addFolloweeElement(K2, followee[i]);
		}
		
		// Now, delete everything.
		for (int i = 0; i < local.length; ++i)
		{
			registry.removeLocalElement(local[i]);
			registry.removeFolloweeElement(K2, followee[i]);
		}
		
		// Register all the listeners.
		FakeListener listener1 = new FakeListener();
		FakeListener listener2 = new FakeListener();
		registry.getReadOnlyConnector(K1).registerListener(listener1, 0);
		registry.getReadOnlyConnector(K2).registerListener(listener2, 0);
		
		// Verify that these are all empty.
		Assert.assertEquals(0, listener1.keysInOrder.size());
		Assert.assertEquals(0, listener2.keysInOrder.size());
	}

	@Test
	public void postDelete() throws Throwable
	{
		// Populate a bunch of entries from 2 users, attach the listeners, then delete them all and verify that the list is now empty.
		EntryCacheRegistry.Builder builder = new EntryCacheRegistry.Builder((Runnable run) -> run.run());
		builder.createConnector(K1);
		EntryCacheRegistry registry = builder.buildRegistry(K1);
		
		// Now, synthesize a new followee.
		registry.createNewFollowee(K2);
		
		// Store all the data.
		IpfsFile[] local = new IpfsFile[5];
		IpfsFile[] followee = new IpfsFile[5];
		for (int i = 0; i < local.length; ++i)
		{
			local[i] = MockSingleNode.generateHash(new byte[] { (byte)i });
			registry.addLocalElement(local[i]);
			followee[i] = MockSingleNode.generateHash(new byte[] { (byte)(i * 2) });
			registry.addFolloweeElement(K2, followee[i]);
		}
		
		// Register all the listeners.
		FakeListener listener1 = new FakeListener();
		FakeListener listener2 = new FakeListener();
		registry.getReadOnlyConnector(K1).registerListener(listener1, 0);
		registry.getReadOnlyConnector(K2).registerListener(listener2, 0);
		
		// Now, delete everything.
		for (int i = 0; i < local.length; ++i)
		{
			registry.removeLocalElement(local[i]);
			registry.removeFolloweeElement(K2, followee[i]);
		}
		
		// Verify that these are all empty but we saw the deletes.
		Assert.assertEquals(0, listener1.keysInOrder.size());
		Assert.assertEquals(5, listener1.deleteCount);
		Assert.assertEquals(0, listener2.keysInOrder.size());
		Assert.assertEquals(5, listener2.deleteCount);
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
