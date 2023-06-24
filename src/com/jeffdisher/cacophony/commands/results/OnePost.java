package com.jeffdisher.cacophony.commands.results;

import java.io.PrintStream;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * An implementation of the result type for the cases where a single post is read or modified.
 * It may provide a new root but will always provide the record CID and StreamRecord.
 */
public class OnePost implements ICommand.Result
{
	private final IpfsFile _newRoot;
	public final IpfsFile recordCid;
	public final AbstractRecord streamRecord;

	public OnePost(IpfsFile newRoot, IpfsFile recordCid, AbstractRecord streamRecord)
	{
		Assert.assertTrue(null != recordCid);
		Assert.assertTrue(null != streamRecord);
		_newRoot = newRoot;
		this.recordCid = recordCid;
		this.streamRecord = streamRecord;
	}

	@Override
	public IpfsFile getIndexToPublish()
	{
		return _newRoot;
	}

	@Override
	public void writeHumanReadable(PrintStream output)
	{
		output.println("Post with name \"" + this.streamRecord.getName() + "\": " + this.recordCid);
	}
}
