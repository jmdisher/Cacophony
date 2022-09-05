package com.jeffdisher.cacophony.data;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;


public class TestLocalDataModel
{
	@Test
	public void createAndReadEmpty() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		fileSystem.createConfigDirectory();
		
		LocalDataModel model = new LocalDataModel(fileSystem);
		IReadWriteLocalData access = model.openForWrite();
		Assert.assertNull(access.readFollowIndex());
		Assert.assertNull(access.readGlobalPinCache());
		Assert.assertNull(access.readGlobalPrefs());
		Assert.assertNull(access.readLocalIndex());
		access.writeGlobalPrefs(GlobalPrefs.defaultPrefs());
		access.close();
		
		IReadOnlyLocalData reader = model.openForRead();
		GlobalPrefs prefs = reader.readGlobalPrefs();
		reader.close();
		GlobalPrefs defaults = GlobalPrefs.defaultPrefs();
		Assert.assertEquals(defaults, prefs);
	}

	@Test
	public void concurrentReaders() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		fileSystem.createConfigDirectory();
		
		// Create the model.
		LocalDataModel model = new LocalDataModel(fileSystem);
		IReadWriteLocalData access = model.openForWrite();
		Assert.assertNull(access.readFollowIndex());
		Assert.assertNull(access.readGlobalPinCache());
		Assert.assertNull(access.readGlobalPrefs());
		Assert.assertNull(access.readLocalIndex());
		access.writeGlobalPrefs(GlobalPrefs.defaultPrefs());
		access.close();
		
		// Create a bunch of threads with a barrier to synchronize them inside the read lock.
		Thread[] threads = new Thread[3];
		CyclicBarrier barrier = new CyclicBarrier(threads.length);
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i] = new Thread(() -> {
				IReadOnlyLocalData reader = model.openForRead();
				try
				{
					barrier.await();
				}
				catch (InterruptedException e)
				{
					Assert.fail();
				}
				catch (BrokenBarrierException e)
				{
					Assert.fail();
				}
				reader.close();
			});
		}
		
		// Start the threads.
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i].start();
		}
		
		// Wait for them to finish (this will block forever if they are not concurrently accessing the read lock).
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i].join();
		}
	}

	@Test
	public void serializedWriters() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem();
		fileSystem.createConfigDirectory();
		
		// Create the model.
		LocalDataModel model = new LocalDataModel(fileSystem);
		IReadWriteLocalData access = model.openForWrite();
		Assert.assertNull(access.readFollowIndex());
		Assert.assertNull(access.readGlobalPinCache());
		Assert.assertNull(access.readGlobalPrefs());
		Assert.assertNull(access.readLocalIndex());
		access.writeGlobalPrefs(GlobalPrefs.defaultPrefs());
		access.close();
		
		// Create a bunch of threads with an atomic counter to verify that nobody is ever inside the write lock at the same time.
		// NOTE:  This is racy but should only rarely pass when it is actually broken.
		Thread[] threads = new Thread[3];
		CyclicBarrier barrier = new CyclicBarrier(threads.length);
		AtomicReference<Thread> owner = new AtomicReference<>();
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i] = new Thread(() -> {
				// Synchronize thread start-up on the barrier.
				try
				{
					barrier.await();
				}
				catch (InterruptedException e)
				{
					Assert.fail();
				}
				catch (BrokenBarrierException e)
				{
					Assert.fail();
				}
				// Now, fight over the write lock.
				IReadWriteLocalData writer = model.openForWrite();
				// When we get the lock, increment the counter to make sure nobody else is here, and wait for a small amount of time.
				Assert.assertTrue(owner.compareAndSet(null, Thread.currentThread()));
				try
				{
					Thread.sleep(100L);
				}
				catch (InterruptedException e)
				{
					Assert.fail();
				}
				Assert.assertTrue(owner.compareAndSet(Thread.currentThread(), null));
				writer.close();
			});
		}
		
		// Start the threads.
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i].start();
		}
		
		// Wait for them to finish (this will block forever if they are not concurrently accessing the read lock).
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i].join();
		}
	}
}
