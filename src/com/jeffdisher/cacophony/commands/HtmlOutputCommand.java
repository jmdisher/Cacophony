package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


/**
 * Walks the existing data in the local cache, creating and populating the existing directory with a static site
 * including all of this information.
 * This is a simple way to view everything prior to the dynamic content in the WebUI which will be added in version 2.0.
 */
public record HtmlOutputCommand(File _directory) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		if (_directory.exists())
		{
			throw new UsageException("Directory already exists: " + _directory);
		}
		LocalConfig local = environment.loadExistingConfig();
		
		// We need the local index.
		LocalIndex localIndex = local.readLocalIndex();
		
		IOperationLog log = environment.logOperation("Generating static HTML output in " + _directory);
		
		// Create the connection before creating the directory.
		IConnection connection =  local.getSharedConnection();
		if (!_directory.mkdir())
		{
			throw new UsageException("Directory cannot be created: " + _directory);
		}
		
		// Write the static files in the directory.
		PrintWriter generatedStream;
		try {
			_writeStaticFile(_directory, "index.html");
			_writeStaticFile(_directory, "prefs.html");
			_writeStaticFile(_directory, "utils.js");
			_writeStaticFile(_directory, "user.html");
			_writeStaticFile(_directory, "play.html");
			_writeStaticFile(_directory, "recommending.html");
			_writeStaticFile(_directory, "following.html");
			
			generatedStream = new PrintWriter(new FileOutputStream(new File(_directory, "generated_db.js")));
		}
		catch (IOException e)
		{
			throw new UsageException(e.getMessage());
		}
		
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		LoadChecker checker = new LoadChecker(scheduler, local.loadGlobalPinCache(), connection);
		IpfsKey ourPublicKey = scheduler.getPublicKey();
		IpfsFile lastPublishedIndex = localIndex.lastPublishedIndex();
		GlobalPrefs prefs = local.readSharedPrefs();
		FollowIndex followIndex = local.loadFollowIndex();
		
		// Now, write the generated_db.js.
		String comment = "Note that this file is generated by HtmlOutputCommand.";
		JsonGenerationHelpers.generateJsonDb(generatedStream, comment, checker, ourPublicKey, lastPublishedIndex, prefs, followIndex);
		generatedStream.close();
		
		log.finish("HTML interface generation complete!  Point your browser at: " + new File(_directory, "index.html").toURI());
	}


	private void _writeStaticFile(File directory, String fileName) throws IOException
	{
		String path = "/resources/site/" + fileName;
		InputStream stream = HtmlOutputCommand.class.getResourceAsStream(path);
		Files.copy(stream, new File(directory, fileName).toPath());
		stream.close();
	}
}
