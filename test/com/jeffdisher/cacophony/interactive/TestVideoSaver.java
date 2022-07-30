package com.jeffdisher.cacophony.interactive;

import java.io.File;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.logic.DraftManager;


public class TestVideoSaver
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	@Test
	public void testWriteVideo() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftManager manager = new DraftManager(directory);
		manager.createNewDraft(1);
		VideoSaver saver = new VideoSaver(manager, 1);
		saver.append(new byte[] { 1, 2, 3 }, 0, 3);
		saver.append(new byte[] { 3, 4, 5 }, 1, 2);
		long written = saver.sockedDidClose();
		Assert.assertEquals(5L, written);
		
		byte[] readBack = Files.readAllBytes(manager.openExistingDraft(1).originalVideo().toPath());
		Assert.assertArrayEquals(new byte[] { 1, 2, 3, 4, 5}, readBack);
	}

	@Test
	public void testOverwriteVideo() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftManager manager = new DraftManager(directory);
		manager.createNewDraft(1);
		VideoSaver saver = new VideoSaver(manager, 1);
		saver.append(new byte[] { 7, 8, 9 }, 0, 3);
		long written = saver.sockedDidClose();
		Assert.assertEquals(3L, written);
		
		saver = new VideoSaver(manager, 1);
		saver.append(new byte[] { 1, 2, 3 }, 0, 3);
		saver.append(new byte[] { 3, 4, 5 }, 1, 2);
		written = saver.sockedDidClose();
		Assert.assertEquals(5L, written);
		
		byte[] readBack = Files.readAllBytes(manager.openExistingDraft(1).originalVideo().toPath());
		Assert.assertArrayEquals(new byte[] { 1, 2, 3, 4, 5}, readBack);
	}
}
