package com.jeffdisher.cacophony.access;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.scheduler.FutureSave;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.FutureUnpin;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestConcurrentTransaction
{
	public static final IpfsKey K1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	public static final IpfsKey K2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14W");
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final IpfsFile F3 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC");

	@Test
	public void createAndCommitEmpty() throws Throwable
	{
		FakeNetworkScheduler network = new FakeNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Collections.emptySet());
		transaction.commit(target);
		Assert.assertTrue(target.changedPinCounts.isEmpty());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertTrue(network.pinned.isEmpty());
	}

	@Test
	public void createAndCommitOnePin() throws Throwable
	{
		FakeNetworkScheduler network = new FakeNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Collections.emptySet());
		transaction.pin(F1);
		transaction.commit(target);
		Assert.assertEquals(1, target.changedPinCounts.size());
		Assert.assertEquals(1, target.changedPinCounts.get(F1).intValue());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(1, network.pinned.size());
	}

	@Test
	public void createAndRollbackOnePin() throws Throwable
	{
		FakeNetworkScheduler network = new FakeNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Collections.emptySet());
		transaction.pin(F1);
		transaction.rollback(target);
		Assert.assertTrue(target.changedPinCounts.isEmpty());
		Assert.assertEquals(1, target.falsePins.size());
		// StandardAccess would normally revert the pin so here we only see that it went through.
		Assert.assertEquals(1, network.pinned.size());
	}

	@Test
	public void unpinMultipleCommit() throws Throwable
	{
		FakeNetworkScheduler network = new FakeNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1, F2, F3));
		transaction.unpin(F1);
		transaction.unpin(F2);
		transaction.commit(target);
		Assert.assertEquals(2, target.changedPinCounts.size());
		Assert.assertEquals(-1, target.changedPinCounts.get(F1).intValue());
		Assert.assertEquals(-1, target.changedPinCounts.get(F2).intValue());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertTrue(network.pinned.isEmpty());
	}

	@Test
	public void unpinMultipleRollback() throws Throwable
	{
		FakeNetworkScheduler network = new FakeNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1, F2, F3));
		transaction.unpin(F1);
		transaction.unpin(F2);
		transaction.rollback(target);
		Assert.assertTrue(target.changedPinCounts.isEmpty());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertTrue(network.pinned.isEmpty());
	}

	@Test
	public void changePinCommit() throws Throwable
	{
		FakeNetworkScheduler network = new FakeNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1, F2, F3));
		transaction.pin(F1);
		transaction.unpin(F2);
		transaction.commit(target);
		Assert.assertEquals(2, target.changedPinCounts.size());
		Assert.assertEquals(1, target.changedPinCounts.get(F1).intValue());
		Assert.assertEquals(-1, target.changedPinCounts.get(F2).intValue());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertTrue(network.pinned.isEmpty());
	}

	@Test
	public void changePinRollback() throws Throwable
	{
		FakeNetworkScheduler network = new FakeNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1, F2, F3));
		transaction.pin(F1);
		transaction.unpin(F2);
		transaction.rollback(target);
		Assert.assertTrue(target.changedPinCounts.isEmpty());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertTrue(network.pinned.isEmpty());
	}

	@Test
	public void loadTesting() throws Throwable
	{
		FakeNetworkScheduler network = new FakeNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1));
		network.nextSize = 5L;
		Assert.assertEquals(5L, transaction.getSizeInBytes(F1).get());
		transaction.pin(F1);
		transaction.pin(F2);
		network.nextSize = 6L;
		Assert.assertEquals(6L, transaction.getSizeInBytes(F2).get());
		network.nextRead = "one".getBytes();
		Assert.assertEquals("one", transaction.loadCached(F1, (byte[] data) -> new String(data)).get());
		network.nextRead = "two".getBytes();
		Assert.assertEquals("two", transaction.loadCached(F2, (byte[] data) -> new String(data)).get());
		transaction.commit(target);
		
		Assert.assertEquals(2, target.changedPinCounts.size());
		Assert.assertEquals(1, target.changedPinCounts.get(F1).intValue());
		Assert.assertEquals(1, target.changedPinCounts.get(F2).intValue());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(1, network.pinned.size());
	}


	private static class FakeWritingAccess implements ConcurrentTransaction.IStateResolver
	{
		public Map<IpfsFile, Integer> changedPinCounts;
		public Set<IpfsFile> falsePins;
		
		@Override
		public void commitTransactionPinCanges(Map<IpfsFile, Integer> changedPinCounts, Set<IpfsFile> falsePins)
		{
			this.changedPinCounts = changedPinCounts;
			this.falsePins = falsePins;
		}
	}


	private static class FakeNetworkScheduler implements INetworkScheduler
	{
		public byte[] nextRead;
		public long nextSize;
		public Set<IpfsFile> pinned = new HashSet<>();
		@Override
		public <R> FutureRead<R> readData(IpfsFile file, DataDeserializer<R> decoder)
		{
			FutureRead<R> read = new FutureRead<>();
			Assert.assertTrue(null != this.nextRead);
			try
			{
				read.success(decoder.apply(this.nextRead));
			}
			catch (FailedDeserializationException e)
			{
				read.failureInDecoding(e);
			}
			this.nextRead = null;
			return read;
		}
		@Override
		public FutureSave saveStream(InputStream stream, boolean shouldCloseStream)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public FuturePublish publishIndex(IpfsFile indexHash)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public FutureSize getSizeInBytes(IpfsFile cid)
		{
			FutureSize size = new FutureSize();
			Assert.assertTrue(this.nextSize > 0L);
			size.success(this.nextSize);
			this.nextSize = 0L;
			return size;
		}
		@Override
		public IpfsKey getPublicKey()
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public FuturePin pin(IpfsFile cid)
		{
			Assert.assertFalse(this.pinned.contains(cid));
			this.pinned.add(cid);
			FuturePin pin = new FuturePin();
			pin.success();
			return pin;
		}
		@Override
		public FutureUnpin unpin(IpfsFile cid)
		{
			Assert.assertTrue(this.pinned.contains(cid));
			this.pinned.remove(cid);
			FutureUnpin unpin = new FutureUnpin();
			unpin.success();
			return unpin;
		}
		@Override
		public void shutdown()
		{
			throw new AssertionError("Not in test");
		}
	}
}
