package com.jeffdisher.cacophony.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v2.IFolloweeDecoding;
import com.jeffdisher.cacophony.data.local.v2.IMiscUses;
import com.jeffdisher.cacophony.data.local.v2.OpcodeContext;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.MiscHelpers;


public class TestLocalDataModel
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	public static final IpfsKey K1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	public static final IpfsKey K2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14W");
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final IpfsFile F3 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC");

	@Test
	public void createAndReadEmpty() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(null);
		fileSystem.createConfigDirectory();
		
		LocalDataModel model = new LocalDataModel(fileSystem);
		IReadWriteLocalData access = model.openForWrite();
		access.writeLocalIndex(ChannelData.create("ipfs", "key"));
		access.writeGlobalPrefs(PrefsData.defaultPrefs());
		access.writeGlobalPinCache(PinCacheData.createEmpty());
		access.writeFollowIndex(FolloweeData.createEmpty());
		access.close();
		
		IReadOnlyLocalData reader = model.openForRead();
		PrefsData prefs = reader.readGlobalPrefs();
		reader.close();
		PrefsData defaults = PrefsData.defaultPrefs();
		Assert.assertEquals(defaults.videoEdgePixelMax, prefs.videoEdgePixelMax);
		Assert.assertEquals(defaults.followCacheTargetBytes, prefs.followCacheTargetBytes);
	}

	@Test
	public void concurrentReaders() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(null);
		fileSystem.createConfigDirectory();
		
		// Create the model.
		LocalDataModel model = new LocalDataModel(fileSystem);
		IReadWriteLocalData access = model.openForWrite();
		access.writeLocalIndex(ChannelData.create("ipfs", "key"));
		access.writeGlobalPrefs(PrefsData.defaultPrefs());
		access.writeGlobalPinCache(PinCacheData.createEmpty());
		access.writeFollowIndex(FolloweeData.createEmpty());
		access.close();
		
		// Create a bunch of threads with a barrier to synchronize them inside the read lock.
		Thread[] threads = new Thread[3];
		CyclicBarrier barrier = new CyclicBarrier(threads.length);
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i] = MiscHelpers.createThread(() -> {
				IReadOnlyLocalData reader = null;
				try
				{
					reader = model.openForRead();
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
			}, "TestLocalDataModel thread #" + i);
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
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(null);
		fileSystem.createConfigDirectory();
		
		// Create the model.
		LocalDataModel model = new LocalDataModel(fileSystem);
		IReadWriteLocalData access = model.openForWrite();
		access.writeLocalIndex(ChannelData.create("ipfs", "key"));
		access.writeGlobalPrefs(PrefsData.defaultPrefs());
		access.writeGlobalPinCache(PinCacheData.createEmpty());
		access.writeFollowIndex(FolloweeData.createEmpty());
		access.close();
		
		// Create a bunch of threads with an atomic counter to verify that nobody is ever inside the write lock at the same time.
		// NOTE:  This is racy but should only rarely pass when it is actually broken.
		Thread[] threads = new Thread[3];
		CyclicBarrier barrier = new CyclicBarrier(threads.length);
		AtomicReference<Thread> owner = new AtomicReference<>();
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i] = MiscHelpers.createThread(() -> {
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
			}, "TestLocalDataModel thread #" + i);
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
	public void basicOpcodeStream() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(null);
		fileSystem.createConfigDirectory();
		
		// Create the model with some minimal data.
		LocalDataModel model = new LocalDataModel(fileSystem);
		try (IReadWriteLocalData access = model.openForWrite())
		{
			access.writeLocalIndex(ChannelData.create("host", "key"));
			access.writeGlobalPrefs(PrefsData.defaultPrefs());
			access.writeFollowIndex(FolloweeData.createEmpty());
			access.writeGlobalPinCache(PinCacheData.createEmpty());
		}
		byte[] serialized = _serializeModelToOpcodes(model);
		
		// Replay the stream to make sure it is what we expected to see.
		CountingCallbacks counting = new CountingCallbacks();
		OpcodeContext context = new OpcodeContext(counting, counting);
		context.decodeWholeStream(new ByteArrayInputStream(serialized));
		Assert.assertEquals(5, counting.miscCount);
		Assert.assertEquals(0, counting.followeeCount);
	}

	@Test
	public void fullOpcodeStream() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(null);
		fileSystem.createConfigDirectory();
		
		// Create the model with enough data to see positive opcode generated.
		LocalDataModel model = new LocalDataModel(fileSystem);
		try (IReadWriteLocalData access = model.openForWrite())
		{
			access.writeLocalIndex(ChannelData.create("host", "key"));
			access.writeGlobalPrefs(PrefsData.defaultPrefs());
			FolloweeData followees = FolloweeData.createEmpty();
			followees.createNewFollowee(K1, F1);
			followees.addElement(K1, new FollowingCacheElement(F1, F2, null, 5L));
			followees.updateExistingFollowee(K1, F1, 1L);
			access.writeFollowIndex(followees);
			PinCacheData pinCache = PinCacheData.createEmpty();
			pinCache.addRef(F1);
			pinCache.addRef(F2);
			pinCache.addRef(F1);
			access.writeGlobalPinCache(pinCache);
		}
		byte[] serialized = _serializeModelToOpcodes(model);
		
		// Replay the stream to make sure it is what we expected to see.
		CountingCallbacks counting = new CountingCallbacks();
		OpcodeContext context = new OpcodeContext(counting, counting);
		context.decodeWholeStream(new ByteArrayInputStream(serialized));
		Assert.assertEquals(7, counting.miscCount);
		Assert.assertEquals(2, counting.followeeCount);
	}

	@Test
	public void corruptData() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(null);
		
		// Create the directory with valid version but nothing else.
		fileSystem.createConfigDirectory();
		fileSystem.writeTrivialFile("version", new byte[] {1});
		
		// Verify that this throws a version error.
		LocalDataModel model = new LocalDataModel(fileSystem);
		boolean error = false;
		try
		{
			model.verifyStorageConsistency();
		}
		catch (UsageException e)
		{
			error = true;
		}
		Assert.assertTrue(error);
	}

	/**
	 * Just tests the different failure cases around inconsistent local data.
	 */
	@Test
	public void inconsistentData() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(null);
		LocalDataModel model = new LocalDataModel(fileSystem);
		
		// We assert that we verify data only AFTER creating the storage.
		boolean didFail = false;
		try
		{
			model.verifyStorageConsistency();
		}
		catch (AssertionError e)
		{
			// This is an assertion since it would be a bug in the code.
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		// We throw a usage error if the version file is missing so create the directory, but nothing else.
		fileSystem.createConfigDirectory();
		didFail = false;
		try
		{
			model.verifyStorageConsistency();
		}
		catch (UsageException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		// We throw a usage error if the version file is not a supported version.
		fileSystem.writeTrivialFile("version", new byte[] {1});
		didFail = false;
		try
		{
			model.verifyStorageConsistency();
		}
		catch (UsageException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		// We throw a usage error if the opcode log is missing.
		fileSystem.writeTrivialFile("version", new byte[] {2});
		didFail = false;
		try
		{
			model.verifyStorageConsistency();
		}
		catch (UsageException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		// We don't validate the the log is correct, just that it exists.
		try (IConfigFileSystem.AtomicOutputStream stream = fileSystem.writeAtomicFile("opcodes_0.final.gzlog"))
		{
			stream.commit();
		}
		model.verifyStorageConsistency();
	}

	@Test
	public void draftDirectory() throws Throwable
	{
		File input = FOLDER.newFolder();
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(input);
		File drafts = fileSystem.getDraftsTopLevelDirectory();
		Assert.assertEquals(input, drafts);
	}


	private byte[] _serializeModelToOpcodes(LocalDataModel model) throws IOException
	{
		byte[] serialized = null;
		try (IReadOnlyLocalData access = model.openForRead())
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ObjectOutputStream stream = OpcodeContext.createOutputStream(bytes);
			access.readLocalIndex().serializeToOpcodeStream(stream);
			access.readGlobalPrefs().serializeToOpcodeStream(stream);
			access.readGlobalPinCache().serializeToOpcodeStream(stream);
			access.readFollowIndex().serializeToOpcodeStream(stream);
			stream.close();
			serialized = bytes.toByteArray();
		}
		return serialized;
	}


	private static class CountingCallbacks implements IMiscUses, IFolloweeDecoding
	{
		public int miscCount;
		public int followeeCount;
		
		@Override
		public void createNewFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis)
		{
			this.followeeCount += 1;
		}
		@Override
		public void addElement(IpfsKey followeeKey, IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedSizeBytes)
		{
			this.followeeCount += 1;
		}
		
		@Override
		public void createConfig(String ipfsHost, String keyName)
		{
			this.miscCount += 1;
		}
		@Override
		public void setLastPublishedIndex(IpfsFile lastPublishedIndex)
		{
			this.miscCount += 1;
		}
		@Override
		public void setPinnedCount(IpfsFile cid, int count)
		{
			this.miscCount += 1;
		}
		@Override
		public void setPrefsKey(String keyName, Serializable value)
		{
			this.miscCount += 1;
		}
	}
}
