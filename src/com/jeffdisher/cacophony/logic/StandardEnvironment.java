package com.jeffdisher.cacophony.logic;

import java.io.PrintStream;

import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;
import com.jeffdisher.cacophony.utils.Assert;


public class StandardEnvironment implements IEnvironment
{
	private final PrintStream _stream;
	private final IConfigFileSystem _fileSystem;
	private final IConnectionFactory _factory;
	private final boolean _shouldEnableVerifications;
	private int _nextOperationCounter;
	private LocalConfig _lazyConfig;
	private boolean _errorOccurred;

	public StandardEnvironment(PrintStream stream, IConfigFileSystem fileSystem, IConnectionFactory factory, boolean shouldEnableVerifications)
	{
		_stream = stream;
		_fileSystem = fileSystem;
		_factory = factory;
		_shouldEnableVerifications = shouldEnableVerifications;
		_nextOperationCounter = 0;
	}

	@Override
	public void logToConsole(String message)
	{
		_stream.println(message);
	}

	/**
	 * Used to create a logging object associated with this opening message so that the completion of the operation will
	 * be associated with it.
	 * 
	 * @param openingMessage The message to log when opening the log option.
	 * @return An object which can receive the log message for when this is finished.
	 */
	@Override
	public IOperationLog logOperation(String openingMessage)
	{
		int operationNumber = _nextOperationCounter + 1;
		_nextOperationCounter += 1;
		_stream.println(">" + operationNumber + " " + openingMessage);
		return (finishMessage) -> _stream.println("<" + operationNumber + " " + finishMessage);
	}

	@Override
	public void logError(String message)
	{
		System.err.println(message);
		_errorOccurred = true;
	}

	@Override
	public LocalConfig createNewConfig(String ipfsConnectionString, String keyName) throws UsageException
	{
		// We cannot create a config if we already loaded one.
		Assert.assertTrue(null == _lazyConfig);
		_lazyConfig = LocalConfig.createNewConfig(_fileSystem, _factory, ipfsConnectionString, keyName);
		// We expect the above to throw if it fails.
		Assert.assertTrue(null != _lazyConfig);
		return _lazyConfig;
	}

	@Override
	public LocalConfig loadExistingConfig() throws UsageException, VersionException
	{
		if (null == _lazyConfig)
		{
			_lazyConfig = LocalConfig.loadExistingConfig(_fileSystem, _factory);
			// We expect the above to throw if it fails.
			Assert.assertTrue(null != _lazyConfig);
		}
		return _lazyConfig;
	}

	@Override
	public boolean shouldEnableVerifications()
	{
		return _shouldEnableVerifications;
	}

	public boolean didErrorOccur()
	{
		return _errorOccurred;
	}
}
