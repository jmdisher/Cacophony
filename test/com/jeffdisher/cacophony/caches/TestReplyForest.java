package com.jeffdisher.cacophony.caches;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.HandoffConnector.IHandoffListener;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestReplyForest
{
	@Test
	public void empty() throws Throwable
	{
		ReplyForest forest = new ReplyForest();
		HandoffConnector<IpfsFile, IpfsFile> connector = new HandoffConnector<IpfsFile, IpfsFile>((Runnable r) -> r.run());
		IpfsFile missingRoot = MockSingleNode.generateHash(new byte[] {1});
		ReplyForest.IAdapterToken adapter = forest.addListener(connector, missingRoot);
		// The root is not known so it should fail to register.
		Assert.assertNull(adapter);
	}

	@Test
	public void single() throws Throwable
	{
		ReplyForest forest = new ReplyForest();
		HandoffConnector<IpfsFile, IpfsFile> connector = new HandoffConnector<IpfsFile, IpfsFile>((Runnable r) -> r.run());
		IpfsFile parent = MockSingleNode.generateHash(new byte[] {1});
		IpfsFile child = MockSingleNode.generateHash(new byte[] {2});
		forest.addPost(child, parent);
		ReplyForest.IAdapterToken adapter = forest.addListener(connector, parent);
		FakeListener listener = new FakeListener();
		connector.registerListener(listener, 0);
		Assert.assertEquals(1, listener.childrenToParents.size());
		Assert.assertEquals(parent, listener.childrenToParents.get(child));
		forest.removeListener(adapter);
	}

	@Test
	public void singleAndRemove() throws Throwable
	{
		ReplyForest forest = new ReplyForest();
		HandoffConnector<IpfsFile, IpfsFile> connector = new HandoffConnector<IpfsFile, IpfsFile>((Runnable r) -> r.run());
		IpfsFile parent = MockSingleNode.generateHash(new byte[] {1});
		IpfsFile child = MockSingleNode.generateHash(new byte[] {2});
		forest.addPost(child, parent);
		ReplyForest.IAdapterToken adapter = forest.addListener(connector, parent);
		FakeListener listener = new FakeListener();
		connector.registerListener(listener, 0);
		forest.removePost(child, parent);
		Assert.assertEquals(0, listener.childrenToParents.size());
		forest.removeListener(adapter);
	}

	@Test
	public void complexRoot() throws Throwable
	{
		ReplyForest forest = new ReplyForest();
		HandoffConnector<IpfsFile, IpfsFile> connector = new HandoffConnector<IpfsFile, IpfsFile>((Runnable r) -> r.run());
		IpfsFile A = MockSingleNode.generateHash(new byte[] {1});
		IpfsFile B = MockSingleNode.generateHash(new byte[] {2});
		IpfsFile AA = MockSingleNode.generateHash(new byte[] {3});
		IpfsFile AB = MockSingleNode.generateHash(new byte[] {4});
		IpfsFile AAA = MockSingleNode.generateHash(new byte[] {5});
		IpfsFile BA = MockSingleNode.generateHash(new byte[] {6});
		forest.addPost(AA, A);
		forest.addPost(AB, A);
		forest.addPost(AAA, AA);
		forest.addPost(BA, B);
		ReplyForest.IAdapterToken adapter = forest.addListener(connector, A);
		FakeListener listener = new FakeListener();
		connector.registerListener(listener, 0);
		// We don't see the B tree.
		Assert.assertEquals(3, listener.childrenToParents.size());
		Assert.assertEquals(A, listener.childrenToParents.get(AA));
		Assert.assertEquals(A, listener.childrenToParents.get(AB));
		Assert.assertEquals(AA, listener.childrenToParents.get(AAA));
		forest.removeListener(adapter);
	}

	@Test
	public void complexBranch() throws Throwable
	{
		ReplyForest forest = new ReplyForest();
		HandoffConnector<IpfsFile, IpfsFile> connector = new HandoffConnector<IpfsFile, IpfsFile>((Runnable r) -> r.run());
		IpfsFile A = MockSingleNode.generateHash(new byte[] {1});
		IpfsFile AA = MockSingleNode.generateHash(new byte[] {3});
		IpfsFile AB = MockSingleNode.generateHash(new byte[] {4});
		IpfsFile AAA = MockSingleNode.generateHash(new byte[] {5});
		forest.addPost(AA, A);
		forest.addPost(AB, A);
		forest.addPost(AAA, AA);
		ReplyForest.IAdapterToken adapter = forest.addListener(connector, AA);
		FakeListener listener = new FakeListener();
		connector.registerListener(listener, 0);
		// We only see what is rooted in AA.
		Assert.assertEquals(1, listener.childrenToParents.size());
		Assert.assertEquals(AA, listener.childrenToParents.get(AAA));
		forest.removeListener(adapter);
	}

	@Test
	public void complexRemoval() throws Throwable
	{
		ReplyForest forest = new ReplyForest();
		HandoffConnector<IpfsFile, IpfsFile> connector = new HandoffConnector<IpfsFile, IpfsFile>((Runnable r) -> r.run());
		IpfsFile A = MockSingleNode.generateHash(new byte[] {1});
		IpfsFile B = MockSingleNode.generateHash(new byte[] {2});
		IpfsFile AA = MockSingleNode.generateHash(new byte[] {3});
		IpfsFile AB = MockSingleNode.generateHash(new byte[] {4});
		IpfsFile AAA = MockSingleNode.generateHash(new byte[] {5});
		IpfsFile BA = MockSingleNode.generateHash(new byte[] {6});
		forest.addPost(AA, A);
		forest.addPost(AB, A);
		forest.addPost(AAA, AA);
		forest.addPost(BA, B);
		ReplyForest.IAdapterToken adapter = forest.addListener(connector, A);
		FakeListener listener = new FakeListener();
		connector.registerListener(listener, 0);
		// Now, do the removals.
		forest.removePost(BA, B);
		forest.removePost(AB, A);
		
		// We don't see the B tree.
		Assert.assertEquals(2, listener.childrenToParents.size());
		Assert.assertEquals(A, listener.childrenToParents.get(AA));
		Assert.assertEquals(AA, listener.childrenToParents.get(AAA));
		forest.removeListener(adapter);
	}

	@Test
	public void complexDynamic() throws Throwable
	{
		ReplyForest forest = new ReplyForest();
		HandoffConnector<IpfsFile, IpfsFile> connector = new HandoffConnector<IpfsFile, IpfsFile>((Runnable r) -> r.run());
		IpfsFile A = MockSingleNode.generateHash(new byte[] {1});
		IpfsFile B = MockSingleNode.generateHash(new byte[] {2});
		IpfsFile AA = MockSingleNode.generateHash(new byte[] {3});
		IpfsFile AB = MockSingleNode.generateHash(new byte[] {4});
		IpfsFile AAA = MockSingleNode.generateHash(new byte[] {5});
		IpfsFile BA = MockSingleNode.generateHash(new byte[] {6});
		forest.addPost(AA, A);
		
		// Register everything after the first relationship.
		ReplyForest.IAdapterToken adapter = forest.addListener(connector, A);
		FakeListener listener = new FakeListener();
		connector.registerListener(listener, 0);
		
		// Add the rest of the tree.
		forest.addPost(AB, A);
		forest.addPost(AAA, AA);
		forest.addPost(BA, B);
		
		// Check the size and relationships.
		Assert.assertEquals(3, listener.childrenToParents.size());
		Assert.assertEquals(A, listener.childrenToParents.get(AA));
		Assert.assertEquals(A, listener.childrenToParents.get(AB));
		Assert.assertEquals(AA, listener.childrenToParents.get(AAA));
		
		// Now, do the removals.
		forest.removePost(BA, B);
		forest.removePost(AB, A);
		
		// Check, again.
		Assert.assertEquals(2, listener.childrenToParents.size());
		Assert.assertEquals(A, listener.childrenToParents.get(AA));
		Assert.assertEquals(AA, listener.childrenToParents.get(AAA));
		forest.removeListener(adapter);
	}


	private static class FakeListener implements IHandoffListener<IpfsFile, IpfsFile>
	{
		// Remember that, through this interface, the "child" is the key.
		public final Map<IpfsFile, IpfsFile> childrenToParents = new HashMap<>();
		
		@Override
		public boolean create(IpfsFile key, IpfsFile value, boolean isNewest)
		{
			Assert.assertFalse(this.childrenToParents.containsKey(key));
			this.childrenToParents.put(key, value);
			return true;
		}
		@Override
		public boolean update(IpfsFile key, IpfsFile value)
		{
			// Not called.
			Assert.fail();
			return false;
		}
		@Override
		public boolean destroy(IpfsFile key)
		{
			Assert.assertTrue(this.childrenToParents.containsKey(key));
			this.childrenToParents.remove(key);
			return true;
		}
		@Override
		public boolean specialChanged(String special)
		{
			// Not called.
			Assert.fail();
			return false;
		}
	}
}
