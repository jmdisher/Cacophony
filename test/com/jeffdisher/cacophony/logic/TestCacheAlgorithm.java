package com.jeffdisher.cacophony.logic;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;


public class TestCacheAlgorithm
{
	@Test
	public void testBytesAvailable()
	{
		CacheAlgorithm manager = new CacheAlgorithm(10, 0);
		Assert.assertEquals(10, manager.getBytesAvailable());
	}

	@Test
	public void testRemovingFromNonFull()
	{
		CacheAlgorithm.Candidate<Void> candidate = new CacheAlgorithm.Candidate<Void>(5, null);
		CacheAlgorithm manager = new CacheAlgorithm(10, 9);
		List<CacheAlgorithm.Candidate<Void>> toRemove = manager.toRemoveInResize(List.of(candidate));
		Assert.assertTrue(toRemove.isEmpty());
		Assert.assertEquals(1, manager.getBytesAvailable());
	}

	@Test
	public void testRemovingFromFull()
	{
		CacheAlgorithm.Candidate<Void> candidate = new CacheAlgorithm.Candidate<Void>(5, null);
		CacheAlgorithm manager = new CacheAlgorithm(10, 12);
		List<CacheAlgorithm.Candidate<Void>> toRemove = manager.toRemoveInResize(List.of(candidate));
		Assert.assertEquals(1, toRemove.size());
		Assert.assertEquals(3, manager.getBytesAvailable());
	}

	@Test
	public void testAddAll()
	{
		CacheAlgorithm.Candidate<Void> candidate1 = new CacheAlgorithm.Candidate<Void>(5, null);
		CacheAlgorithm.Candidate<Void> candidate2 = new CacheAlgorithm.Candidate<Void>(57, null);
		long startSize = 1;
		CacheAlgorithm manager = new CacheAlgorithm(100, startSize);
		// The add is probabilistic so this can be any size but shouldn't fail.
		List<CacheAlgorithm.Candidate<Void>> toAdd = manager.toAddInNewAddition(List.of(candidate1, candidate2));
		Assert.assertTrue(toAdd.size() <= 2);
		long sizeNow = startSize + toAdd.stream().mapToLong(c -> c.byteSize()).sum();
		Assert.assertEquals(100 - sizeNow, manager.getBytesAvailable());
		// Make sure there was no overflow.
		Assert.assertTrue(manager.getBytesAvailable() >= 0);
	}

	@Test
	public void testAddSome()
	{
		CacheAlgorithm.Candidate<Void> candidate1 = new CacheAlgorithm.Candidate<Void>(5, null);
		CacheAlgorithm.Candidate<Void> candidate2 = new CacheAlgorithm.Candidate<Void>(57, null);
		CacheAlgorithm.Candidate<Void> candidate3 = new CacheAlgorithm.Candidate<Void>(57, null);
		CacheAlgorithm.Candidate<Void> candidate4 = new CacheAlgorithm.Candidate<Void>(57, null);
		long startSize = 34;
		CacheAlgorithm manager = new CacheAlgorithm(100, startSize);
		// The add is probabilistic so this can be any size but shouldn't overflow.
		List<CacheAlgorithm.Candidate<Void>> toAdd = manager.toAddInNewAddition(List.of(candidate1, candidate2, candidate3, candidate4));
		long sizeNow = startSize + toAdd.stream().mapToLong(c -> c.byteSize()).sum();
		Assert.assertEquals(100 - sizeNow, manager.getBytesAvailable());
		// Make sure there was no overflow.
		Assert.assertTrue(manager.getBytesAvailable() >= 0);
	}
}
