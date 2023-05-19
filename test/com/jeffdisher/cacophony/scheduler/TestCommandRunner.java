package com.jeffdisher.cacophony.scheduler;

import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.types.CacophonyException;


public class TestCommandRunner
{
	@Test
	public void basicPass() throws Throwable
	{
		Context context = _buildContext();
		CommandRunner runner = new CommandRunner(context);
		TestCommand command = new TestCommand();
		command.shouldPass = true;
		FutureCommand<None> result = runner.runCommand(command);
		Assert.assertEquals(None.NONE, result.get());
		Assert.assertEquals("name", result.getContext().keyName);
	}

	@Test
	public void basicError() throws Throwable
	{
		Context context = _buildContext();
		CommandRunner runner = new CommandRunner(context);
		TestCommand command = new TestCommand();
		command.shouldPass = false;
		FutureCommand<None> result = runner.runCommand(command);
		boolean didThrow;
		try
		{
			result.get();
			didThrow = false;
		}
		catch (CacophonyException e)
		{
			didThrow = true;
		}
		Assert.assertTrue(didThrow);
		Assert.assertEquals("name", result.getContext().keyName);
	}


	private static Context _buildContext()
	{
		return new Context(null
				, null
				, null
				, null
				, null
				, null
				, new HashMap<>()
				, "name"
		);
	}


	private static class TestCommand implements ICommand<None>
	{
		public boolean shouldPass;
		@Override
		public None runInContext(Context context) throws CacophonyException
		{
			if (this.shouldPass)
			{
				return None.NONE;
			}
			else
			{
				throw new CacophonyException("TEST");
			}
		}
	}
}
