package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.OutputStream;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.data.local.v1.SizedElement;


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
		IDraftWrapper wrapper = manager.createNewDraft(1);
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
		IDraftWrapper wrapper1 = manager.createNewDraft(1);
		Assert.assertEquals(1, wrapper1.loadDraft().id());
		IDraftWrapper wrapper2 = manager.createNewDraft(2);
		Assert.assertEquals(2, wrapper2.loadDraft().id());
		List<Draft> drafts = manager.listAllDrafts();
		Assert.assertEquals(2, drafts.size());
		manager.deleteExistingDraft(1);
		manager.deleteExistingDraft(2);
		Assert.assertEquals(0, manager.listAllDrafts().size());
	}

	@Test
	public void testDeleteFailure() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftManager manager = new DraftManager(directory);
		IDraftWrapper wrapper1 = manager.createNewDraft(1);
		
		try (OutputStream output = wrapper1.writeOriginalVideo())
		{
			Assert.assertFalse(manager.deleteExistingDraft(1));
		}
		List<Draft> drafts = manager.listAllDrafts();
		Assert.assertEquals(1, drafts.size());
	}

	@Test
	public void json() throws Throwable
	{
		File directory = FOLDER.newFolder();
		DraftManager manager = new DraftManager(directory);
		IDraftWrapper wrapper = manager.createNewDraft(1);
		SizedElement audio = new SizedElement("audio/ogg", 0, 0, 5L);
		wrapper.updateDraftUnderLock((Draft oldDraft) ->
			new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), oldDraft.originalVideo(), oldDraft.processedVideo(), audio)
		);
		JsonObject out = wrapper.loadDraft().toJson();
		String raw = out.toString();
		JsonObject in = Json.parse(raw).asObject();
		Draft read = Draft.fromJson(in);
		Assert.assertEquals(1, read.id());
		Assert.assertNull(read.originalVideo());
		Assert.assertEquals(0, read.audio().height());
	}
}
