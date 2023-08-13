package com.jeffdisher.cacophony.caches;

import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestHomeUserReplyCache
{
	@Test
	public void startStopEmpty()
	{
		HandoffConnector<IpfsFile, IpfsFile> connector = new HandoffConnector<>(DISPATCHER);
		Listener listener = new Listener();
		connector.registerListener(listener, 10);
		new HomeUserReplyCache(connector);
		connector.unregisterListener(listener);
		Assert.assertEquals(0, listener.createCount);
		Assert.assertEquals(0, listener.destroyCount);
	}

	@Test
	public void addRemoveBasic()
	{
		HandoffConnector<IpfsFile, IpfsFile> connector = new HandoffConnector<>(DISPATCHER);
		Listener listener = new Listener();
		connector.registerListener(listener, 10);
		HomeUserReplyCache cache = new HomeUserReplyCache(connector);
		IpfsFile h1 = MockSingleNode.generateHash(new byte[] { 1 });
		IpfsFile h2 = MockSingleNode.generateHash(new byte[] { 2 });
		IpfsFile f1 = MockSingleNode.generateHash(new byte[] { 3 });
		IpfsFile f2 = MockSingleNode.generateHash(new byte[] { 4 });
		cache.addHomePost(h1);
		cache.addHomePost(h2);
		cache.addFolloweePost(f1, h1);
		cache.addFolloweePost(f2, f1);
		cache.removeFolloweePost(f1);
		connector.unregisterListener(listener);
		Assert.assertEquals(1, listener.createCount);
		Assert.assertEquals(1, listener.destroyCount);
	}

	@Test
	public void addRemoveMultiple()
	{
		HandoffConnector<IpfsFile, IpfsFile> connector = new HandoffConnector<>(DISPATCHER);
		Listener listener = new Listener();
		connector.registerListener(listener, 10);
		HomeUserReplyCache cache = new HomeUserReplyCache(connector);
		IpfsFile h1 = MockSingleNode.generateHash(new byte[] { 1 });
		IpfsFile f1 = MockSingleNode.generateHash(new byte[] { 2 });
		IpfsFile f2 = MockSingleNode.generateHash(new byte[] { 3 });
		cache.addHomePost(h1);
		cache.addFolloweePost(f1, h1);
		cache.addFolloweePost(f1, h1);
		cache.addFolloweePost(f2, h1);
		cache.removeFolloweePost(f1);
		cache.removeFolloweePost(f1);
		connector.unregisterListener(listener);
		Assert.assertEquals(2, listener.createCount);
		Assert.assertEquals(1, listener.destroyCount);
	}

	@Test
	public void attachLate()
	{
		HandoffConnector<IpfsFile, IpfsFile> connector = new HandoffConnector<>(DISPATCHER);
		HomeUserReplyCache cache = new HomeUserReplyCache(connector);
		IpfsFile h1 = MockSingleNode.generateHash(new byte[] { 1 });
		IpfsFile f1 = MockSingleNode.generateHash(new byte[] { 2 });
		IpfsFile f2 = MockSingleNode.generateHash(new byte[] { 3 });
		cache.addHomePost(h1);
		cache.addFolloweePost(f1, h1);
		cache.addFolloweePost(f1, h1);
		cache.addFolloweePost(f2, h1);
		Listener listener = new Listener();
		connector.registerListener(listener, 10);
		cache.removeFolloweePost(f1);
		cache.removeFolloweePost(f1);
		connector.unregisterListener(listener);
		Assert.assertEquals(2, listener.createCount);
		Assert.assertEquals(1, listener.destroyCount);
	}


	public static Consumer<Runnable> DISPATCHER = new Consumer<>() {
		@Override
		public void accept(Runnable arg0)
		{
			synchronized (this)
			{
				arg0.run();
			}
		}
	};

	public static class Listener implements HandoffConnector.IHandoffListener<IpfsFile, IpfsFile>
	{
		public int createCount = 0;
		public int destroyCount = 0;
		
		@Override
		public boolean create(IpfsFile key, IpfsFile value, boolean isNewest)
		{
			this.createCount += 1;
			return true;
		}
		@Override
		public boolean update(IpfsFile key, IpfsFile value)
		{
			throw new AssertionError("Not tested");
		}
		@Override
		public boolean destroy(IpfsFile key)
		{
			this.destroyCount += 1;
			return true;
		}
		@Override
		public boolean specialChanged(String special)
		{
			throw new AssertionError("Not tested");
		}
	}
}
