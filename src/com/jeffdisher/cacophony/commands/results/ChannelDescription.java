package com.jeffdisher.cacophony.commands.results;

import java.io.PrintStream;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * An implementation of the result type for the cases where a channel's description was read or modified.
 * It may provide a new root but will always provide the StreamDescription and userPicUrl.
 */
public class ChannelDescription implements ICommand.Result
{
	private final IpfsFile _newRoot;
	public final String name;
	public final String description;
	public final IpfsFile userPicCid;
	public final String email;
	public final String website;
	public final String userPicUrl;
	public final IpfsFile feature;

	public ChannelDescription(IpfsFile newRoot
			, String name
			, String description
			, IpfsFile userPicCid
			, String email
			, String website
			, String userPicUrl
			, IpfsFile feature
	)
	{
		// newRoot is null when this result isn't changing anything.
		Assert.assertTrue(null != name);
		Assert.assertTrue(null != description);
		// email and website can be null.
		Assert.assertTrue(null != userPicUrl);
		
		_newRoot = newRoot;
		this.name = name;
		this.description = description;
		this.userPicCid = userPicCid;
		this.email = email;
		this.website = website;
		this.userPicUrl = userPicUrl;
		this.feature = feature;
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
		output.println("\tName: " + this.name);
		output.println("\tDescription: " + this.description);
		output.println("\tUser pic: " + this.userPicUrl);
		output.println("\tE-Mail: " + this.email);
		output.println("\tWebsite: " + this.website);
		output.println("\tFeature: " + this.feature);
	}
}
