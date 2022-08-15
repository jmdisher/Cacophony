package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.local.v1.Draft;


public class TestDraftManager
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	@Test
	public void testWriteAndReadNew() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftManager manager = new DraftManager(directory);
		Assert.assertEquals(0, manager.listAllDrafts().size());
		DraftWrapper wrapper = manager.createNewDraft(1);
		Assert.assertEquals(1, wrapper.loadDraft().id());
		List<Draft> drafts = manager.listAllDrafts();
		Assert.assertEquals(1, drafts.size());
		Assert.assertEquals(1, drafts.get(0).id());
	}

	@Test
	public void testNewAndDelete() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftManager manager = new DraftManager(directory);
		Assert.assertEquals(0, manager.listAllDrafts().size());
		DraftWrapper wrapper1 = manager.createNewDraft(1);
		Assert.assertEquals(1, wrapper1.loadDraft().id());
		DraftWrapper wrapper2 = manager.createNewDraft(2);
		Assert.assertEquals(2, wrapper2.loadDraft().id());
		List<Draft> drafts = manager.listAllDrafts();
		Assert.assertEquals(2, drafts.size());
		manager.deleteExistingDraft(1);
		manager.deleteExistingDraft(2);
		Assert.assertEquals(0, manager.listAllDrafts().size());
	}
}