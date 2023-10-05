package com.jeffdisher.cacophony.commands.results;

import java.io.PrintStream;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * An implementation of the result type for the cases where a list of keys is returned.
 */
public class KeyList implements ICommand.Result
{
	private final String _elementDescription;
	public final IpfsKey[] keys;

	/**
	 * Creates a key list with a description of what they represent.
	 * 
	 * @param elementDescription The human-readable description of what the keys represent.
	 * @param keys The list of keys.
	 */
	public KeyList(String elementDescription, IpfsKey[] keys)
	{
		Assert.assertTrue(null != keys);
		Assert.assertTrue(null != elementDescription);
		_elementDescription = elementDescription;
		this.keys = keys;
	}

	@Override
	public IpfsFile getIndexToPublish()
	{
		return null;
	}

	@Override
	public void writeHumanReadable(PrintStream output)
	{
		output.println(this.keys.length + " keys in list:");
		for (IpfsKey key : this.keys)
		{
			output.println("\t" + _elementDescription + ": " + key.toPublicKey());
		}
	}
}
