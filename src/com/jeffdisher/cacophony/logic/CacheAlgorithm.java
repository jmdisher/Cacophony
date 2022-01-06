package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.List;


/**
 * A cache management algorithm which is intended to be used by a higher-level cache manager of some sort.
 * The implementation operates only on the data given to it, with no on-disk representation, so it should be applicable
 * to cache managers which want to do per-channel cache accounting or global cache accounting, equally well.
 * The general idea is to facilitate cache decisions which favour adding/retaining entries at the beginning of the given
 * lists, meaning the lists can be ordered to favour recent entries, large/small entries, or some other ordering.
 * The cache decisions are internally random but will tend toward being mostly full but will not make overflowing
 * decisions, itself (although it can handle the cases where it asked to overflow).
 */
public class CacheAlgorithm
{
	/**
	 * The type used to communicate cache decisions through this API.
	 * 
	 * @param <T> The type of arbitrary user data required by the user's implementation.
	 */
	public static record Candidate<T>(long byteSize, T data)
	{
	}


	private final long _maximumSizeBytes;
	private long _currentSizeBytes;

	/**
	 * Creates the cache algorithm with the given limit and initial state.
	 * 
	 * @param maximumSizeBytes The maximum size the cache should be allowed to become, in bytes.
	 * @param currentSizeBytes The current occupancy of the cache, in bytes.
	 */
	public CacheAlgorithm(long maximumSizeBytes, long currentSizeBytes)
	{
		_maximumSizeBytes = maximumSizeBytes;
		_currentSizeBytes = currentSizeBytes;
	}

	/**
	 * @return The number of bytes currently available in the cache.
	 */
	public long getBytesAvailable()
	{
		return (_maximumSizeBytes - _currentSizeBytes);
	}

	/**
	 * Updates the internal size of the cache to assume bytesAdded has been added by some external decision-maker.
	 * 
	 * @param bytesAdded The number of bytes explicitly added to the cache.
	 * @return True if the cache is now overflowing and needs to be cleaned.
	 */
	public boolean needsCleanAfterAddition(long bytesAdded)
	{
		_currentSizeBytes += bytesAdded;
		return (_currentSizeBytes > _maximumSizeBytes);
	}

	/**
	 * Returns a subset of the given candidatesList which should be removed.  Selection is done at random until the
	 * cache is back within its limits.
	 * 
	 * @param candidatesList The list of cache eviction Candidates.
	 * @return The list of cache Candidates which should be evicted.
	 */
	public <T> List<Candidate<T>> toRemoveInResize(List<Candidate<T>> candidatesList)
	{
		List<Candidate<T>> candidates = new ArrayList<>(candidatesList);
		List<Candidate<T>> evictions = new ArrayList<>();
		while (!candidates.isEmpty() && (_currentSizeBytes > _maximumSizeBytes))
		{
			int index = (int)((double)candidates.size() * Math.random());
			Candidate<T> candidate = candidates.remove(index);
			evictions.add(candidate);
			_currentSizeBytes -= candidate.byteSize;
		}
		return evictions;
	}

	/**
	 * Called when a new channel is to be cached.  The implementation will walk candidatesList, returning a subset which
	 * should be added to the cache.  The algorithm favours the entries at the beginning of the list for selection.
	 * Note that the cache must be cleaned before adding this list or it may not add anything as it will not overflow.
	 * 
	 * @param candidatesList The list of Candidates to consider for adding to the cache.
	 * @return The list of Candidates which should be added to the cache (may be empty if the cache is full).
	 */
	public <T> List<Candidate<T>> toAddInNewAddition(List<Candidate<T>> candidatesList)
	{
		List<Candidate<T>> additions = new ArrayList<>();
		for (Candidate<T> candidate : candidatesList)
		{
			if (_currentSizeBytes > _maximumSizeBytes)
			{
				// Exit case will be if we fill up (could be first iteration so we check before changing anything)
				break;
			}
			else if ((_currentSizeBytes + candidate.byteSize) > _maximumSizeBytes)
			{
				// This would have caused overflow so skip it.
			}
			else
			{
				double chanceToAdd = 1.0 - ((double)_currentSizeBytes / (double)_maximumSizeBytes);
				if (Math.random() < chanceToAdd)
				{
					// This has been selected for addition.
					additions.add(candidate);
					_currentSizeBytes += candidate.byteSize;
				}
			}
		}
		return additions;
	}
}
