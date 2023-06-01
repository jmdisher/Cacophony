package com.jeffdisher.cacophony.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v3.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v3.OpcodeContext;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
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
		
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		
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
		
		// Create the model.
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		
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
		
		// Create the model.
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		
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
		
		// Create the model with some minimal data.
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		byte[] serialized = _serializeModelToOpcodes(model);
		
		// Replay the stream to make sure it is what we expected to see.
		ChannelData channelData = null;
		PrefsData prefs = PrefsData.defaultPrefs();
		FolloweeData followees = null;
		ExplicitCacheData explicitCache = null;
		OpcodeContext context = new OpcodeContext(channelData, prefs, followees, null, explicitCache);
		OpcodeCodec.decodeWholeStream(new ByteArrayInputStream(serialized), context);
		Assert.assertEquals(PrefsData.DEFAULT_VIDEO_EDGE, prefs.videoEdgePixelMax);
		Assert.assertEquals(PrefsData.DEFAULT_FOLLOW_CACHE_BYTES, prefs.followCacheTargetBytes);
		Assert.assertEquals(PrefsData.DEFAULT_FOLLOWEE_REFRESH_MILLIS, prefs.followeeRefreshMillis);
		Assert.assertEquals(PrefsData.DEFAULT_REPUBLISH_INTERVAL_MILLIS, prefs.republishIntervalMillis);
		Assert.assertEquals(PrefsData.DEFAULT_EXPLICIT_CACHE_BYTES, prefs.explicitCacheTargetBytes);
	}

	@Test
	public void fullOpcodeStream() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(null);
		
		// Create the model with enough data to see positive opcode generated.
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		try (IReadWriteLocalData access = model.openForWrite())
		{
			FolloweeData followees = access.readFollowIndex();
			followees.createNewFollowee(K1, F1, 0L);
			followees.addElement(K1, new FollowingCacheElement(F1, F2, null, 5L));
			followees.updateExistingFollowee(K1, F1, 1L);
			access.writeFollowIndex(followees);
			PinCacheData pinCache = access.readGlobalPinCache();
			pinCache.addRef(F1);
			pinCache.addRef(F2);
			pinCache.addRef(F1);
			access.writeGlobalPinCache(pinCache);
		}
		byte[] serialized = _serializeModelToOpcodes(model);
		
		// Replay the stream to make sure it is what we expected to see.
		ChannelData channelData = null;
		PrefsData prefs = PrefsData.defaultPrefs();
		FolloweeData followees = FolloweeData.createEmpty();
		ExplicitCacheData explicitCache = new ExplicitCacheData();
		OpcodeContext context = new OpcodeContext(channelData, prefs, followees, null, explicitCache);
		OpcodeCodec.decodeWholeStream(new ByteArrayInputStream(serialized), context);
		Set<IpfsKey> knownKeys = followees.getAllKnownFollowees();
		Assert.assertEquals(1, knownKeys.size());
		IpfsKey followee = knownKeys.iterator().next();
		Assert.assertEquals(K1, followee);
		Assert.assertEquals(F1, followees.getLastFetchedRootForFollowee(followee));
		
	}

	@Test
	public void corruptData() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(null);
		
		// Create the directory with valid version but nothing else.
		fileSystem.createConfigDirectory();
		fileSystem.writeTrivialFile("version", new byte[] {1});
		
		// Verify that this throws a version error.
		boolean error = false;
		try
		{
			LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
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
		
		// We throw a usage error if the version file is missing so create the directory, but nothing else.
		fileSystem.createConfigDirectory();
		boolean didFail = false;
		try
		{
			LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
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
			LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		}
		catch (UsageException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		// We throw a usage error if the opcode log is missing.
		fileSystem.writeTrivialFile("version", new byte[] {3});
		didFail = false;
		try
		{
			LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		}
		catch (UsageException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		// We need the log to exist and contain valid data, so use the defaults we use when initializing it before running a command.
		try (IConfigFileSystem.AtomicOutputStream atomic = fileSystem.writeAtomicFile("opcodes.v3.gzlog"))
		{
			try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(atomic.getStream()))
			{
				ChannelData.create().serializeToOpcodeWriter(writer);
				PrefsData.defaultPrefs().serializeToOpcodeWriter(writer);
				FolloweeData.createEmpty().serializeToOpcodeWriter(writer);
			}
			atomic.commit();
		}
		LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
	}

	@Test
	public void draftDirectory() throws Throwable
	{
		File input = FOLDER.newFolder();
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(input);
		File drafts = fileSystem.getDraftsTopLevelDirectory();
		Assert.assertEquals(input, drafts);
	}

	@Test
	public void createDestroyChannel() throws Throwable
	{
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(null);
		
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		
		try (IReadWriteLocalData reader = model.openForWrite())
		{
			ChannelData channels = reader.readLocalIndex();
			Assert.assertEquals(0, channels.getKeyNames().size());
			channels.setLastPublishedIndex("key1", K1, F1);
		}
		
		try (IReadWriteLocalData reader = model.openForWrite())
		{
			ChannelData channels = reader.readLocalIndex();
			Assert.assertEquals(1, channels.getKeyNames().size());
			channels.removeChannel("key1");
		}
		
		try (IReadOnlyLocalData reader = model.openForRead())
		{
			ChannelData channels = reader.readLocalIndex();
			Assert.assertEquals(0, channels.getKeyNames().size());
		}
	}


	private byte[] _serializeModelToOpcodes(LocalDataModel model) throws IOException
	{
		byte[] serialized = null;
		try (IReadOnlyLocalData access = model.openForRead())
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(bytes))
			{
				access.readLocalIndex().serializeToOpcodeWriter(writer);
				access.readGlobalPrefs().serializeToOpcodeWriter(writer);
				access.readFollowIndex().serializeToOpcodeWriter(writer);
			}
			serialized = bytes.toByteArray();
		}
		return serialized;
	}
}
