package com.jeffdisher.cacophony.access;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.testutils.MockNetworkScheduler;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestConcurrentTransaction
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] {3});

	@Test
	public void createAndCommitEmpty() throws Throwable
	{
		MockNetworkScheduler network = new MockNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Collections.emptySet());
		transaction.commit(target);
		Assert.assertTrue(target.changedPinCounts.isEmpty());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(0, network.addedPinCount());
	}

	@Test
	public void createAndCommitOnePin() throws Throwable
	{
		MockNetworkScheduler network = new MockNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Collections.emptySet());
		transaction.pin(F1).get();
		transaction.commit(target);
		Assert.assertEquals(1, target.changedPinCounts.size());
		Assert.assertEquals(1, target.changedPinCounts.get(F1).intValue());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(1, network.addedPinCount());
	}

	@Test
	public void createAndRollbackOnePin() throws Throwable
	{
		MockNetworkScheduler network = new MockNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Collections.emptySet());
		transaction.pin(F1).get();
		transaction.rollback(target);
		Assert.assertTrue(target.changedPinCounts.isEmpty());
		Assert.assertEquals(1, target.falsePins.size());
		// StandardAccess would normally revert the pin so here we only see that it went through.
		Assert.assertEquals(1, network.addedPinCount());
	}

	@Test
	public void unpinMultipleCommit() throws Throwable
	{
		MockNetworkScheduler network = new MockNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1, F2, F3));
		transaction.unpin(F1);
		transaction.unpin(F2);
		transaction.commit(target);
		Assert.assertEquals(2, target.changedPinCounts.size());
		Assert.assertEquals(-1, target.changedPinCounts.get(F1).intValue());
		Assert.assertEquals(-1, target.changedPinCounts.get(F2).intValue());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(0, network.addedPinCount());
	}

	@Test
	public void unpinMultipleRollback() throws Throwable
	{
		MockNetworkScheduler network = new MockNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1, F2, F3));
		transaction.unpin(F1);
		transaction.unpin(F2);
		transaction.rollback(target);
		Assert.assertTrue(target.changedPinCounts.isEmpty());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(0, network.addedPinCount());
	}

	@Test
	public void changePinCommit() throws Throwable
	{
		MockNetworkScheduler network = new MockNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1, F2, F3));
		transaction.pin(F1).get();
		transaction.unpin(F2);
		transaction.commit(target);
		Assert.assertEquals(2, target.changedPinCounts.size());
		Assert.assertEquals(1, target.changedPinCounts.get(F1).intValue());
		Assert.assertEquals(-1, target.changedPinCounts.get(F2).intValue());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(0, network.addedPinCount());
	}

	@Test
	public void changePinRollback() throws Throwable
	{
		MockNetworkScheduler network = new MockNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1, F2, F3));
		transaction.pin(F1).get();
		transaction.unpin(F2);
		transaction.rollback(target);
		Assert.assertTrue(target.changedPinCounts.isEmpty());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(0, network.addedPinCount());
	}

	@Test
	public void loadTesting() throws Throwable
	{
		MockNetworkScheduler network = new MockNetworkScheduler();
		FakeWritingAccess target = new FakeWritingAccess();
		IpfsFile data1 = network.storeData("one  ".getBytes());
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(data1));
		Assert.assertEquals(5L, transaction.getSizeInBytes(data1).get());
		transaction.pin(data1).get();
		IpfsFile data2 = network.storeData("two   ".getBytes());
		transaction.pin(data2).get();
		Assert.assertEquals(6L, transaction.getSizeInBytes(data2).get());
		Assert.assertEquals("one  ", transaction.loadCached(data1, (byte[] data) -> new String(data)).get());
		Assert.assertEquals("two   ", transaction.loadCached(data2, (byte[] data) -> new String(data)).get());
		transaction.commit(target);
		
		Assert.assertEquals(2, target.changedPinCounts.size());
		Assert.assertEquals(1, target.changedPinCounts.get(data1).intValue());
		Assert.assertEquals(1, target.changedPinCounts.get(data2).intValue());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(1, network.addedPinCount());
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
}
