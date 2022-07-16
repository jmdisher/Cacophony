package com.jeffdisher.cacophony.logic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jeffdisher.cacophony.utils.Assert;


/**
 * Handles the server-side processing of a video.  The processing is done in background threads, based on the draft and
 * command it is given.
 * The command is assumed to not have any special escapes or quoting (only split args by spaces).
 * The background process will issue callbacks as it processes and when it is done, on its internal threads (but never
 * concurrently).
 * The process can be cancelled from the outside.
 */
public class ExternalStreamProcessor
{
	private final List<String> _commandAndArgs;
	private final ReentrantLock _callbackLock;

	// Set to -1 if the process is still running.
	private int _processReturnValue;
	// This is -1 if the processing is still in process.
	private long _processedVideoBytes;
	private Thread _inputProcessor;
	private Thread _outputProcessor;
	private Thread _errorProcessor;

	public ExternalStreamProcessor(String command)
	{
		String[] parts = command.split(" ");
		_commandAndArgs = Stream.of(parts)
				.map((String part) -> part.trim())
				.filter((String trimmed) -> !trimmed.isEmpty())
				.collect(Collectors.toList());
		_callbackLock = new ReentrantLock();
	}

	/**
	 * Requests that the background video processing operation begins.  Note that the function takes ownership of the
	 * streams it is given.
	 * 
	 * @param originalVideo The data stream containing the original video content.
	 * @param processedVideo The data stream where the processed video will be sent.
	 * @param progressCallback This callback will be passed the number of input bytes processed periodically.
	 * @param errorCallback This callback will be notified of errors or STDERR content.
	 * @param doneCallback This callback will be notified when processing is done and stop should be called.
	 * @throws IOException The process could not be started.
	 */
	public void start(InputStream originalVideo, OutputStream processedVideo, Consumer<Long> progressCallback, Consumer<String> errorCallback, Runnable doneCallback) throws IOException
	{
		// Always odd that Process names these to match the types, but not what the streams are...
		Process process = new ProcessBuilder(_commandAndArgs).start();
		
		_inputProcessor = new Thread(() -> {
			OutputStream inputStream = process.getOutputStream();
			byte[] buffer = new byte[64 * 1024];
			long totalBytes = 0;
			boolean forceFail = false;
			try
			{
				long oneRead = _readOnce(buffer, originalVideo, inputStream);
				while (oneRead > 0)
				{
					totalBytes += oneRead;
					_postProgress(progressCallback, totalBytes);
					oneRead = _readOnce(buffer, originalVideo, inputStream);
				}
				// If this is done, close the input and wait for termination of the process (since this is the feeder thread).
			}
			catch (IOException e)
			{
				_postError(errorCallback, e.getLocalizedMessage());
			}
			catch (InterruptedException e)
			{
				// In this case, we want to force-stop.
				forceFail = true;
			}
			finally
			{
				try
				{
					originalVideo.close();
					inputStream.close();
				}
				catch (IOException e)
				{
					throw Assert.unexpected(e);
				}
			}
			if (forceFail)
			{
				// In this case, just kill the process.
				process.destroyForcibly();
			}
			int result;
			try
			{
				result = process.waitFor();
			}
			catch (InterruptedException e)
			{
				// In this case, the user terminated the operation while we were already waiting for it to finish, so force it.
				forceFail = true;
				process.destroyForcibly();
				try
				{
					result = process.waitFor();
				}
				catch (InterruptedException e1)
				{
					// This second call is not expected.
					throw Assert.unexpected(e1);
				}
			}
			
			// Set the process status and notify the output thread observing the file size, if it beat us there.
			if (0 != result)
			{
				_postError(errorCallback, "Process exit status: " + result);
			}
			_processCompleted(result);
		});
		_outputProcessor = new Thread(() -> {
			InputStream outputStream = process.getInputStream();
			byte[] buffer = new byte[64 * 1024];
			long totalBytes = 0;
			try
			{
				long oneRead = _readOnce(buffer, outputStream, processedVideo);
				while (oneRead > 0)
				{
					totalBytes += oneRead;
					oneRead = _readOnce(buffer, outputStream, processedVideo);
				}
				// The input should be the one waiting on termination so we just clean up and exit here.
			}
			catch (IOException e)
			{
				_postError(errorCallback, e.getLocalizedMessage());
			}
			catch (InterruptedException e)
			{
				// Only the main input thread gets interrupted.
				throw Assert.unexpected(e);
			}
			finally
			{
				try
				{
					processedVideo.close();
					outputStream.close();
				}
				catch (IOException e)
				{
					throw Assert.unexpected(e);
				}
			}
			
			// We now wait for the process to terminate to make sure it was successful before sending the done callback.
			boolean isSuccess = _waitForProcessTermination();
			_postDone(doneCallback, isSuccess ? totalBytes : -1L);
		});
		_errorProcessor = new Thread(() -> {
			InputStream errorStream = process.getErrorStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
			try
			{
				String line = reader.readLine();
				while (null != line)
				{
					_postError(errorCallback, line);
					line = reader.readLine();
				}
			}
			catch (Exception e)
			{
				_postError(errorCallback, e.getLocalizedMessage());
			}
			finally
			{
				try
				{
					reader.close();
				}
				catch (IOException e)
				{
					throw Assert.unexpected(e);
				}
			}
		});
		
		_processReturnValue = -1;
		_processedVideoBytes = -1l;
		_inputProcessor.start();
		_outputProcessor.start();
		_errorProcessor.start();
	}

	/**
	 * Stops the process.
	 * 
	 * @return The size of the processed video or -1 if processing was interrupted.
	 */
	public long stop()
	{
		Assert.assertTrue(Thread.currentThread() != _inputProcessor);
		Assert.assertTrue(Thread.currentThread() != _outputProcessor);
		Assert.assertTrue(Thread.currentThread() != _errorProcessor);
		
		// We synchronize with the threads under the callback lock so check to see if we need to interrupt anyone.
		_callbackLock.lock();
		try
		{
			boolean isDone = -1 != _processReturnValue;
			if (!isDone)
			{
				// We will use interruption to cancel the input thread since it eventually shuts down the pipeline (we don't normally use that but it would make sense here).
				_inputProcessor.interrupt();
			}
		}
		finally
		{
			_callbackLock.unlock();
		}
		
		// Now that the process is interrupted, wait for everything to exit.
		try
		{
			_inputProcessor.join();
			_outputProcessor.join();
			_errorProcessor.join();
			_inputProcessor = null;
			_outputProcessor = null;
			_errorProcessor = null;
		}
		catch (InterruptedException e)
		{
			// We don't expect the calling thread to be interrupted.
			throw Assert.unexpected(e);
		}
		
		// Now, just return the processed video bytes (will be left -1 if the process exited with an error).
		return _processedVideoBytes;
	}


	private static long _readOnce(byte[] buffer, InputStream input, OutputStream output) throws IOException, InterruptedException
	{
		if (Thread.interrupted())
		{
			throw new InterruptedException();
		}
		int read = input.read(buffer);
		if (read > 0)
		{
			output.write(buffer, 0, read);
		}
		return read;
	}

	private void _postError(Consumer<String> errorCallback, String error)
	{
		_callbackLock.lock();
		try
		{
			errorCallback.accept(error);
		}
		finally
		{
			_callbackLock.unlock();
		}
	}

	private void _postProgress(Consumer<Long> progressCallback, long progress) throws InterruptedException
	{
		if (Thread.interrupted())
		{
			throw new InterruptedException();
		}
		_callbackLock.lock();
		try
		{
			progressCallback.accept(progress);
		}
		finally
		{
			_callbackLock.unlock();
		}
	}

	private void _postDone(Runnable doneCallback, long totalOutputBytes)
	{
		_callbackLock.lock();
		try
		{
			_processedVideoBytes = totalOutputBytes;
			doneCallback.run();
		}
		finally
		{
			_callbackLock.unlock();
		}
	}

	// We need to use the monitor to interact with _processReturnValue to avoid a race condition between the input
	// thread (observing the process exit status) and the output thread (observing the output size).
	private synchronized void _processCompleted(int result)
	{
		Assert.assertTrue(-1 == _processReturnValue);
		Assert.assertTrue(result >= 0);
		_processReturnValue = result;
		this.notifyAll();
	}

	private synchronized boolean _waitForProcessTermination()
	{
		while (-1 == _processReturnValue)
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// This thread doesn't receive interrupts.
				throw Assert.unexpected(e);
			}
		}
		return (0 == _processReturnValue);
	}
}
