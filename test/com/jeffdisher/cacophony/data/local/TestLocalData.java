package com.jeffdisher.cacophony.data.local;

import org.junit.Assert;
import org.junit.Test;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.data.local.v4.Draft;
import com.jeffdisher.cacophony.data.local.v4.SizedElement;


/**
 * Test cases for serialization-related tests associated with local data storage.
 */
public class TestLocalData {
	@Test
	public void testSizedElementJson() throws Throwable
	{
		String mime = "image/jpeg";
		int height = 720;
		int width = 1280;
		long byteSize = 12345L;
		SizedElement initial = new SizedElement(mime, height, width, byteSize);
		String json = initial.toJson().toString();
		SizedElement result = SizedElement.fromJson((JsonObject) Json.parse(json));
		Assert.assertEquals(mime, result.mime());
		Assert.assertEquals(height, result.height());
		Assert.assertEquals(width, result.width());
		Assert.assertEquals(byteSize, result.byteSize());
	}

	@Test
	public void testDraftJson() throws Throwable
	{
		String mime = "image/jpeg";
		int height = 720;
		int width = 1280;
		long byteSize = 12345L;
		SizedElement thumbnail = new SizedElement(mime, height, width, byteSize);
		int id = 1;
		long publishedSecondsUtc = 50L;
		String title = "title";
		String description = "description";
		String discussionUrl = "URL";
		Draft initial = new Draft(id, publishedSecondsUtc, title, description, discussionUrl, thumbnail, null, null, null, null);
		String json = initial.toJson().toString();
		Draft result = Draft.fromJson((JsonObject) Json.parse(json));
		Assert.assertEquals(id, result.id());
		Assert.assertEquals(publishedSecondsUtc, result.publishedSecondsUtc());
		Assert.assertEquals(title, result.title());
		Assert.assertEquals(description, result.description());
		Assert.assertEquals(discussionUrl, result.discussionUrl());
		Assert.assertEquals(thumbnail, result.thumbnail());
		Assert.assertEquals(null, result.originalVideo());
		Assert.assertEquals(null, result.processedVideo());
	}

	@Test
	public void missingReplyTo() throws Throwable
	{
		String raw = "{\"id\":1,\"publishedSecondsUtc\":50,\"title\":\"title\",\"description\":\"description\",\"thumbnail\":null,\"discussionUrl\":\"\",\"originalVideo\":null,\"processedVideo\":null,\"audio\":null}\n";
		Draft result = Draft.fromJson((JsonObject) Json.parse(raw));
		Assert.assertEquals(1, result.id());
		Assert.assertEquals("title", result.title());
		Assert.assertNull(result.replyTo());
	}

	@Test
	public void nullReplyTo() throws Throwable
	{
		String raw = "{\"id\":1,\"publishedSecondsUtc\":50,\"title\":\"title\",\"description\":\"description\",\"thumbnail\":null,\"discussionUrl\":\"\",\"originalVideo\":null,\"processedVideo\":null,\"audio\":null,\"replyTo\":null}\n";
		Draft result = Draft.fromJson((JsonObject) Json.parse(raw));
		Assert.assertEquals(1, result.id());
		Assert.assertEquals("title", result.title());
		Assert.assertNull(result.replyTo());
	}
}
