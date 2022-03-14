package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.types.UsageException;


/**
 * This class contains helpers related to command-line option validation since there are some common idioms related to
 * that.
 */
public class ValidationHelpers
{
	/**
	 * Called to verify that the given ILocalActions contains a valid Cacophony configuration.  Will NOT return null,
	 * but will through if missing.
	 * 
	 * @param local The ILocalActions configuration.
	 * @return A non-null LocalIndex.
	 * @throws UsageException Thrown if the LocalIndex could not be loaded.
	 */
	public static LocalIndex requireIndex(ILocalActions local) throws UsageException
	{
		LocalIndex localIndex = local.readIndex();
		if (null == localIndex)
		{
			String configDirectory = local.getConfigDirectoryFullPath();
			throw new UsageException("Error:  No Cacophony configuration found in config directory:  " + configDirectory);
		}
		return localIndex;
	}
}
