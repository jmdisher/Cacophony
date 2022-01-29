package com.jeffdisher.cacophony.commands;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;

import com.jeffdisher.cacophony.logic.IPinMechanism;
import com.jeffdisher.cacophony.types.IpfsFile;


public class MockPinMechanism implements IPinMechanism
{
	private final Set<IpfsFile> _pinned = new HashSet<>();

	@Override
	public void add(IpfsFile cid) throws IOException
	{
		Assert.assertFalse(_pinned.contains(cid));
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
}
