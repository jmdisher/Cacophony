package com.jeffdisher.cacophony.access;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestConcurrentTransaction
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] {3});

	@Test
	public void createAndCommitEmpty() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Collections.emptySet());
		transaction.commit(target);
		Assert.assertTrue(target.changedPinCounts.isEmpty());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(0, node.pinCalls);
		Assert.assertEquals(0, node.getStoredFileSet().size());
		network.shutdown();
	}

	@Test
	public void createAndCommitOnePin() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode upstream = new MockSingleNode(swarm);
		MockSingleNode node = new MockSingleNode(swarm);
		IpfsFile targetFile = upstream.storeData(new ByteArrayInputStream(new byte[] { 1 }));
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Collections.emptySet());
		transaction.pin(targetFile).get();
		transaction.commit(target);
		Assert.assertEquals(1, target.changedPinCounts.size());
		Assert.assertEquals(1, target.changedPinCounts.get(F1).intValue());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(1, node.pinCalls);
		Assert.assertEquals(1, node.getStoredFileSet().size());
		network.shutdown();
	}

	@Test
	public void createAndRollbackOnePin() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode upstream = new MockSingleNode(swarm);
		MockSingleNode node = new MockSingleNode(swarm);
		IpfsFile targetFile = upstream.storeData(new ByteArrayInputStream(new byte[] { 1 }));
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Collections.emptySet());
		transaction.pin(targetFile).get();
		transaction.rollback(target);
		Assert.assertTrue(target.changedPinCounts.isEmpty());
		Assert.assertEquals(1, target.falsePins.size());
		Assert.assertEquals(1, node.pinCalls);
		// StandardAccess would normally revert the pin so here we only see that it went through.
		Assert.assertEquals(1, node.getStoredFileSet().size());
		network.shutdown();
	}

	@Test
	public void unpinMultipleCommit() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1, F2, F3));
		transaction.unpin(F1);
		transaction.unpin(F2);
		transaction.commit(target);
		Assert.assertEquals(2, target.changedPinCounts.size());
		Assert.assertEquals(-1, target.changedPinCounts.get(F1).intValue());
		Assert.assertEquals(-1, target.changedPinCounts.get(F2).intValue());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(0, node.pinCalls);
		Assert.assertEquals(0, node.getStoredFileSet().size());
		network.shutdown();
	}

	@Test
	public void unpinMultipleRollback() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1, F2, F3));
		transaction.unpin(F1);
		transaction.unpin(F2);
		transaction.rollback(target);
		Assert.assertTrue(target.changedPinCounts.isEmpty());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(0, node.pinCalls);
		Assert.assertEquals(0, node.getStoredFileSet().size());
		network.shutdown();
	}

	@Test
	public void changePinCommit() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1, F2, F3));
		transaction.pin(F1).get();
		transaction.unpin(F2);
		transaction.commit(target);
		Assert.assertEquals(2, target.changedPinCounts.size());
		Assert.assertEquals(1, target.changedPinCounts.get(F1).intValue());
		Assert.assertEquals(-1, target.changedPinCounts.get(F2).intValue());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(0, node.pinCalls);
		Assert.assertEquals(0, node.getStoredFileSet().size());
		network.shutdown();
	}

	@Test
	public void changePinRollback() throws Throwable
	{
		MockSingleNode node = new MockSingleNode(new MockSwarm());
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		FakeWritingAccess target = new FakeWritingAccess();
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(F1, F2, F3));
		transaction.pin(F1).get();
		transaction.unpin(F2);
		transaction.rollback(target);
		Assert.assertTrue(target.changedPinCounts.isEmpty());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(0, node.pinCalls);
		Assert.assertEquals(0, node.getStoredFileSet().size());
		network.shutdown();
	}

	@Test
	public void loadTesting() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode upstream = new MockSingleNode(swarm);
		MockSingleNode node = new MockSingleNode(swarm);
		MultiThreadedScheduler network = new MultiThreadedScheduler(node, 1);
		FakeWritingAccess target = new FakeWritingAccess();
		IpfsFile data1 = upstream.storeData(new ByteArrayInputStream("one  ".getBytes()));
		ConcurrentTransaction transaction = new ConcurrentTransaction(network, Set.of(data1));
		Assert.assertEquals(5L, transaction.getSizeInBytes(data1).get());
		transaction.pin(data1).get();
		IpfsFile data2 = upstream.storeData(new ByteArrayInputStream("two   ".getBytes()));
		transaction.pin(data2).get();
		Assert.assertEquals(6L, transaction.getSizeInBytes(data2).get());
		Assert.assertEquals("one  ", transaction.loadCached(data1, (byte[] data) -> new String(data)).get());
		Assert.assertEquals("two   ", transaction.loadCached(data2, (byte[] data) -> new String(data)).get());
		transaction.commit(target);
		
		Assert.assertEquals(2, target.changedPinCounts.size());
		Assert.assertEquals(1, target.changedPinCounts.get(data1).intValue());
		Assert.assertEquals(1, target.changedPinCounts.get(data2).intValue());
		Assert.assertTrue(target.falsePins.isEmpty());
		Assert.assertEquals(1, node.pinCalls);
		Assert.assertEquals(1, node.getStoredFileSet().size());
		network.shutdown();
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
