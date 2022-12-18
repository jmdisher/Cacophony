package com.jeffdisher.cacophony.interactive;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestBackgroundOperations
{
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final IpfsFile F3 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC");


	@Test
	public void noOperations() throws Throwable
	{
		TestOperations ops = new TestOperations();
		BackgroundOperations back = new BackgroundOperations(ops);
		back.startProcess();
		back.shutdownProcess();
	}

	@Test
	public void oneOperation() throws Throwable
	{
		FuturePublish publish = new FuturePublish(F1);
		TestOperations ops = new TestOperations();
		BackgroundOperations back = new BackgroundOperations(ops);
		back.startProcess();
		
		// Enqueue one.
		back.requestPublish(F1);
		ops.returnOn(F1, publish);
		publish.success();
		
		back.shutdownProcess();
	}

	@Test
	public void sequentialOperations() throws Throwable
	{
		FuturePublish publish1 = new FuturePublish(F1);
		FuturePublish publish2 = new FuturePublish(F2);
		TestOperations ops = new TestOperations();
		BackgroundOperations back = new BackgroundOperations(ops);
		back.startProcess();
		
		// Enqueue one, then another.
		back.requestPublish(F1);
		ops.returnOn(F1, publish1);
		publish1.success();
		back.requestPublish(F2);
		ops.returnOn(F2, publish2);
		publish2.success();
		
		back.shutdownProcess();
	}

	@Test
	public void floodedOperations() throws Throwable
	{
		// This one is somewhat non-deterministic in that the first element added may be seen, or could be overwritten.
		// We do know that none of the others will be seen, but then the last will ALWAYS be seen.
		// We will verify this by only setting success on the first and last.
		FuturePublish publishFirst = new FuturePublish(F1);
		FuturePublish publishLast = new FuturePublish(F3);
		TestOperations ops = new TestOperations();
		BackgroundOperations back = new BackgroundOperations(ops);
		back.startProcess();
		
		// Enqueue the one which may or may not be seen, then wait to set its success until we have set all the others.
		back.requestPublish(F1);
		ops.returnOn(F1, publishFirst);
		ops.waitForConsume();
		
		back.requestPublish(F1);
		back.requestPublish(F2);
		back.requestPublish(F3);
		
		publishFirst.success();
		ops.returnOn(F3, publishLast);
		publishLast.success();
		
		back.shutdownProcess();
	}


	private static class TestOperations implements BackgroundOperations.IOperationRunner
	{
		private IpfsFile _match;
		private FuturePublish _return;
		
		@Override
		public synchronized FuturePublish startPublish(IpfsFile newRoot)
		{
			while (!newRoot.equals(_match))
			{
				try
				{
					this.wait();
				}
				catch (InterruptedException e)
				{
					Assert.fail();
				}
			}
			_match = null;
			FuturePublish publish = _return;
			_return = null;
			
			this.notifyAll();
			return publish;
		}
		public synchronized void waitForConsume()
		{
			while (null != _match)
			{
				try
				{
					this.wait();
				}
				catch (InterruptedException e)
				{
					Assert.fail();
				}
			}
		}
		public synchronized void returnOn(IpfsFile file, FuturePublish publish)
		{
			_match = file;
			_return = publish;
			this.notifyAll();
		}
	}
}
