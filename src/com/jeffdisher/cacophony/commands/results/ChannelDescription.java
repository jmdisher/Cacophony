package com.jeffdisher.cacophony.commands.results;

import java.io.PrintStream;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * An implementation of the result type for the cases where a channel's description was read or modified.
 * It may provide a new root but will always provide the StreamDescription and userPicUrl.
 */
public class ChannelDescription implements ICommand.Result
{
	private final IpfsFile _newRoot;
	public final StreamDescription streamDescription;
	public final String userPicUrl;

	public ChannelDescription(IpfsFile newRoot, StreamDescription description, String userPicUrl)
	{
		Assert.assertTrue(null != description);
		Assert.assertTrue(null != userPicUrl);
		_newRoot = newRoot;
		this.streamDescription = description;
		this.userPicUrl = userPicUrl;
	}

	@Override
	public IpfsFile getIndexToPublish()
	{
		return _newRoot;
	}

	@Override
	public void writeHumanReadable(PrintStream output)
	{
		output.println("This channel description:");
		output.println("\tName: " + this.streamDescription.getName());
		output.println("\tDescription: " + this.streamDescription.getDescription());
		output.println("\tUser pic: " + this.userPicUrl);
		output.println("\tE-Mail: " + this.streamDescription.getEmail());
		output.println("\tWebsite: " + this.streamDescription.getWebsite());
	}
}
