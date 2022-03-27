package com.jeffdisher.cacophony.logic;

import java.io.PrintStream;

import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public class StandardEnvironment implements IEnvironment
{
	private final PrintStream _stream;
	private final IConfigFileSystem _fileSystem;
	private final IConnectionFactory _factory;
	private int _nextOperationCounter;
	private LocalConfig _lazyConfig;

	public StandardEnvironment(PrintStream stream, IConfigFileSystem fileSystem, IConnectionFactory factory)
	{
		_stream = stream;
		_fileSystem = fileSystem;
		_factory = factory;
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
	public LocalConfig loadExistingConfig() throws UsageException
	{
		if (null == _lazyConfig)
		{
			_lazyConfig = LocalConfig.loadExistingConfig(_fileSystem, _factory);
			// We expect the above to throw if it fails.
			Assert.assertTrue(null != _lazyConfig);
		}
		return _lazyConfig;
	}
}
