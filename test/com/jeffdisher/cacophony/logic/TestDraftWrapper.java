package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.local.v1.Draft;


public class TestDraftWrapper
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	@Test
	public void testReadWriteData() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftWrapper wrapper = new DraftWrapper(directory);
		
		// We need to write an initial value.
		Draft initial = new Draft(1, 5L, "title", "description", "", null, null, null, null);
		wrapper.saveDraft(initial);
		Draft read = wrapper.loadDraft();
		Assert.assertEquals(initial, read);
	}

	@Test
	public void testConcurrentReaders() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftWrapper wrapper = new DraftWrapper(directory);
		Draft initial = new Draft(1, 5L, "title", "description", "", null, null, null, null);
		wrapper.saveDraft(initial);
		
		byte[] bytes = "TEST".getBytes();
		try (OutputStream stream = wrapper.writeOriginalVideo())
		{
			stream.write(bytes);
		}
		InputStream in1 = wrapper.readOriginalVideo();
		InputStream in2 = wrapper.readOriginalVideo();
		Assert.assertArrayEquals(bytes, in1.readAllBytes());
		Assert.assertArrayEquals(bytes, in2.readAllBytes());
		in1.close();
		in2.close();
	}

	@Test
	public void testUnrelatedWriters() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftWrapper wrapper = new DraftWrapper(directory);
		Draft initial = new Draft(1, 5L, "title", "description", "", null, null, null, null);
		wrapper.saveDraft(initial);
		
		byte[] bytes = "TEST".getBytes();
		OutputStream out1 = wrapper.writeOriginalVideo();
		OutputStream out2 = wrapper.writeProcessedVideo();
		out1.write(bytes);
		out2.write(bytes);
		out1.close();
		
		InputStream in1 = wrapper.readOriginalVideo();
		InputStream in2 = wrapper.readOriginalVideo();
		Assert.assertArrayEquals(bytes, in1.readAllBytes());
		Assert.assertArrayEquals(bytes, in2.readAllBytes());
		in1.close();
		in2.close();
		out2.close();
	}

	@Test
	public void thrashReadersAndWriters() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftWrapper wrapper = new DraftWrapper(directory);
		Draft initial = new Draft(1, 5L, "title", "description", "", null, null, null, null);
		wrapper.saveDraft(initial);
		
		byte[] bytes = "X".getBytes();
		try (OutputStream stream = wrapper.writeOriginalVideo())
		{
			stream.write(bytes);
		}
		ReadThread[] threads = new ReadThread[20];
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i] = new ReadThread(wrapper);
		}
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i].start();
		}
		try (OutputStream out = wrapper.writeOriginalVideo())
		{
			out.write("XXX".getBytes());
		}
		int smallCount = 0;
		int bigCount = 0;
		for (int i = 0; i < threads.length; ++i)
		{
			threads[i].join();
			smallCount += threads[i].smallCount;
			bigCount += threads[i].bigCount;
		}
		Assert.assertEquals(threads.length * 20, smallCount + bigCount);
	}


	private static class ReadThread extends Thread
	{
		private final DraftWrapper _wrapper;
		public int smallCount;
		public int bigCount;
		public ReadThread(DraftWrapper wrapper)
		{
			super("TestDraftWrapper");
			_wrapper = wrapper;
		}
		@Override
		public void run()
		{
			for (int i = 0; i < 20; ++i)
			{
				try (InputStream stream = _wrapper.readOriginalVideo())
				{
					int len = stream.readAllBytes().length;
					if (1 == len)
					{
						this.smallCount += 1;
					}
					else if (3 == len)
					{
						this.bigCount += 1;
					}
				}
				catch (IOException e)
				{
					Assert.fail();
				}
				try
				{
					Thread.sleep(5);
				}
				catch (InterruptedException e)
				{
					Assert.fail();
				}
			}
		}
	}
}
