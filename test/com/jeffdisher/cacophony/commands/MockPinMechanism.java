package com.jeffdisher.cacophony.commands;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;

import org.junit.Assert;

import com.jeffdisher.cacophony.logic.IPinMechanism;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class MockPinMechanism implements IPinMechanism
{
	private final MockConnection _peer;
	private final Set<IpfsFile> _pinned;

	private BiFunction<IpfsFile, byte[], Void> _ingestor;

	public MockPinMechanism(MockConnection peer)
	{
		_peer = peer;
		_pinned = new HashSet<>();
	}

	@Override
	public void add(IpfsFile cid) throws IOException
	{
		Assert.assertFalse(_pinned.contains(cid));
		// This path is assumed to be for remote pins (since that is why this is called) so we must have an attached peer.
		byte[] data = _peer.loadData(cid);
		_ingestor.apply(cid, data);
		_pinned.add(cid);
	}

	@Override
	public void rm(IpfsFile cid) throws IOException
	{
		Assert.assertTrue(_pinned.contains(cid));
		_pinned.remove(cid);
	}

	public boolean isPinned(IpfsFile cid)
	{
		return _pinned.contains(cid);
	}

	public void attachRemoteIngest(BiFunction<IpfsFile, byte[], Void> ingestor)
	{
		_ingestor = ingestor;
	}

	public void addLocalFile(IpfsFile file)
	{
		Assert.assertFalse(_pinned.contains(file));
		_pinned.add(file);
	}

	public IpfsFile remoteResolve(IpfsKey key) throws IOException
	{
		return _peer.resolve(key);
	}
}
