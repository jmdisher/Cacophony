package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.UsageException;


/**
 * Walks the existing data in the local cache, creating and populating the existing directory with a static site
 * including all of this information.
 * This is a simple way to view everything prior to the dynamic content in the WebUI which will be added in version 2.0.
 */
public record HtmlOutputCommand(File _directory) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException, CacophonyException
	{
		if (_directory.exists())
		{
			throw new UsageException("Directory already exists: " + _directory);
		}
		if (!_directory.mkdir())
		{
			throw new UsageException("Directory cannot be created: " + _directory);
		}
		
		// We need the local index.
		LocalIndex localIndex = local.readIndex();
		if (null == localIndex)
		{
			throw new UsageException("Channel must be created before generating HTML output");
		}
		
		// Write the static files in the directory.
		_writeStaticFile(_directory, "index.html");
		_writeStaticFile(_directory, "prefs.html");
		_writeStaticFile(_directory, "utils.js");
		_writeStaticFile(_directory, "user.html");
		_writeStaticFile(_directory, "play.html");
		_writeStaticFile(_directory, "recommending.html");
		_writeStaticFile(_directory, "following.html");
		
		// TODO:  Replace this with a generated variant.
		_writeStaticFile(_directory, "generated_db.js");
	}


	private void _writeStaticFile(File directory, String fileName) throws IOException
	{
		String path = "/resources/site/" + fileName;
		InputStream stream = HtmlOutputCommand.class.getResourceAsStream(path);
		Files.copy(stream, new File(directory, fileName).toPath());
		stream.close();
	}
}
