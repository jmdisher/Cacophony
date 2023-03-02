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
import com.jeffdisher.cacophony.utils.MiscHelpers;


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

	/**
	 * Prepares an external stream processor with the given command but does not start it.
	 * 
	 * @param command The command to run on the host machine.
	 */
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
	 * streams it is given, meaning it will close them when done with them.
	 * The given callbacks may not be issued on the same thread, but won't be issued concurrently.
	 * 
	 * @param originalVideo The data stream containing the original video content.
	 * @param processedVideo The data stream where the processed video will be sent.
	 * @param progressCallback This callback will be passed the number of input bytes processed periodically.
	 * @param errorCallback This callback will be notified of errors or STDERR content.
	 * @param doneCallback This callback will be passed the final number of output bytes when processing is done and
	 * stop should be called.  Note that this no other callbacks will be sent after this one.
	 * @throws IOException The process could not be started.
	 */
	public void start(InputStream originalVideo, OutputStream processedVideo, Consumer<Long> progressCallback, Consumer<String> errorCallback, Consumer<Long> doneCallback) throws IOException
	{
		// Always odd that Process names these to match the types, but not what the streams are...
		Process process;
		try
		{
			process = new ProcessBuilder(_commandAndArgs).start();
		}
		catch (IOException e)
		{
			// The caller still expects us to close the IO streams, even if we failed to start the process.
			originalVideo.close();
			processedVideo.close();
			throw e;
		}
		
		// The _inputProcessor copies the input bytes from the original file into the pipe to the background process.
		// NOTE:  This is where we interrupt the process (allowing a somewhat more predictable shutdown order while
		// keeping the process from being exposed to other threads) when we want to forcibly terminate it, early.
		// For this reason, this thread need to handle asynchronous interruption (it blocks in IO, not a monitor).  Note
		// that the interrupt doesn't actually cause the IO operation to fail but we don't expect to block in IO for any
		// substantial amount of time (as we are just feeding an input pipe) so we check the flag on every copy cycle.
		_inputProcessor = MiscHelpers.createThread(() -> {
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
				}
				catch (IOException e)
				{
					// This normally shouldn't happen but we do observe it in cases where the program exits with an error since the BufferedOutputStream flushes on close.
					forceFail = true;
				}
				try
				{
					inputStream.close();
				}
				catch (IOException e)
				{
					// This normally shouldn't happen but we do observe it in cases where the program exits with an error since the BufferedOutputStream flushes on close.
					forceFail = true;
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
			_processCompleted(result);
		}, "External Processor Input");
		
		// The _outputProcessor copies the processed bytes coming from the pipe from the STDOUT of the background
		// process into the processed video file.
		// NOTE:  This thread is responsible for waiting for the others to shut down and then calling the final finish
		// callback (preceeded by an error callback if the exit code was non-zero).
		_outputProcessor = MiscHelpers.createThread(() -> {
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
			int processExitCode = _waitForProcessTermination();
			try
			{
				// We want for the other threads to terminate (which WILL happen, now that the process has terminated) so that we can force the doneCallback to be the last one we send.
				_inputProcessor.join();
				_errorProcessor.join();
			}
			catch (InterruptedException e)
			{
				// The _inputProcessor thread is the only one which can receive interrupts so we can never observe this exception on this thread.
				throw Assert.unexpected(e);
			}
			_postDone(doneCallback, (0 == processExitCode) ? totalBytes : -1L, errorCallback, processExitCode);
		}, "External Processor Output");
		
		// The _errorProcessor just drains the STDERR of the background process, sending off any data it sees to the
		// error callback.
		_errorProcessor = MiscHelpers.createThread(() -> {
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
		}, "External Processor Error");
		
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
		// Note that this method is only called by the external logic and cannot be invoked reentrantly.
		Assert.assertTrue(Thread.currentThread() != _inputProcessor);
		Assert.assertTrue(Thread.currentThread() != _outputProcessor);
		Assert.assertTrue(Thread.currentThread() != _errorProcessor);
		
		// We synchronize with the threads under the monitor (since what protects _processReturnValue) so check to see if we need to interrupt anyone.
		synchronized(this)
		{
			boolean isDone = (-1 != _processReturnValue);
			if (!isDone)
			{
				// We will use interruption to cancel the input thread since it eventually shuts down the pipeline (we don't normally use that but it would make sense here).
				// (we can't use the monitor since the threads are blocked in IO, not monitor operations)
				_inputProcessor.interrupt();
			}
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


	private long _readOnce(byte[] buffer, InputStream input, OutputStream output) throws IOException, InterruptedException
	{
		// Read operations are performed on the input or output processor threads.
		Assert.assertTrue((Thread.currentThread() == _inputProcessor)
				|| (Thread.currentThread() == _outputProcessor)
		);
		// Note that interrupts are only delivered to the input processor.
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
		// Errors can occur on any of our internal threads.
		Assert.assertTrue((Thread.currentThread() == _inputProcessor)
				|| (Thread.currentThread() == _outputProcessor)
				|| (Thread.currentThread() == _errorProcessor)
		);
		_callbackLock.lock();
		try
		{
			// We can never call this after _postDone is called.
			Assert.assertTrue(-1L == _processedVideoBytes);
			errorCallback.accept(error);
		}
		finally
		{
			_callbackLock.unlock();
		}
	}

	private void _postProgress(Consumer<Long> progressCallback, long progress) throws InterruptedException
	{
		Assert.assertTrue(Thread.currentThread() == _inputProcessor);
		// Note that interrupts are only delivered to the input processor.
		if (Thread.interrupted())
		{
			throw new InterruptedException();
		}
		_callbackLock.lock();
		try
		{
			// We can never call this after _postDone is called.
			Assert.assertTrue(-1L == _processedVideoBytes);
			progressCallback.accept(progress);
		}
		finally
		{
			_callbackLock.unlock();
		}
	}

	private void _postDone(Consumer<Long> doneCallback, long totalOutputBytes, Consumer<String> errorCallback, int processExitCode)
	{
		Assert.assertTrue(Thread.currentThread() == _outputProcessor);
		_callbackLock.lock();
		try
		{
			_processedVideoBytes = totalOutputBytes;
			if (0 != processExitCode)
			{
				// doneCallback is the last one we send so this our last opportunity to report exit status (if we do, it will be the last error reported).
				errorCallback.accept("Process exit status: " + processExitCode);
			}
			doneCallback.accept(totalOutputBytes);
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
		Assert.assertTrue(Thread.currentThread() == _inputProcessor);
		// This can only be called once.
		Assert.assertTrue(-1 == _processReturnValue);
		// Exit status cannot be negative.
		Assert.assertTrue(result >= 0);
		_processReturnValue = result;
		this.notifyAll();
	}

	private synchronized int _waitForProcessTermination()
	{
		Assert.assertTrue(Thread.currentThread() == _outputProcessor);
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
		return _processReturnValue;
	}
}
