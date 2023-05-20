package com.jeffdisher.cacophony.commands;

import java.util.Set;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.KeyList;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsKey;


public record ListFolloweesCommand() implements ICommand<KeyList>
{
	@Override
	public KeyList runInContext(Context context) throws IpfsConnectionException
	{
		KeyList result;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			Set<IpfsKey> followees = access.readableFolloweeData().getAllKnownFollowees();
			IpfsKey[] keys = new IpfsKey[followees.size()];
			int index = 0;
			for(IpfsKey followee : followees)
			{
				keys[index] = followee;
				index += 1;
			}
			result = new KeyList("Following", keys);
		}
		return result;
	}
}
