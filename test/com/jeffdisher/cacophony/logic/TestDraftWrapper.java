package com.jeffdisher.cacophony.logic;

import java.io.File;

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
		Draft initial = new Draft(1, 5L, "title", "description", null, null, null, null);
		wrapper.saveDraft(initial);
		Draft read = wrapper.loadDraft();
		Assert.assertEquals(initial, read);
	}

	@Test
	public void testBinaryFiles() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftWrapper wrapper = new DraftWrapper(directory);
		
		File thumbnail = wrapper.thumbnail();
		File originalVideo = wrapper.originalVideo();
		File processedVideo = wrapper.processedVideo();
		
		// Make sure that they are inside the directory.
		Assert.assertEquals(directory, thumbnail.getParentFile());
		Assert.assertEquals(directory, originalVideo.getParentFile());
		Assert.assertEquals(directory, processedVideo.getParentFile());
		
		// Make sure that they don't yet exist (since this wrapper is new).
		Assert.assertFalse(thumbnail.exists());
		Assert.assertFalse(originalVideo.exists());
		Assert.assertFalse(processedVideo.exists());
	}
}
