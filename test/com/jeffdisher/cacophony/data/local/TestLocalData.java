package com.jeffdisher.cacophony.data.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.data.local.v1.SizedElement;


/**
 * Test cases for serialization-related tests associated with local data storage.
 * For the most part, local storage data types just use basic Java serialization but some have additional constraints,
 * wire serialization encodings, or non-trivial local file representations/organizations.
 */
public class TestLocalData {
	@Test
	public void testSizedElementSerialization() throws Throwable
	{
		String mime = "image/jpeg";
		int height = 720;
		int width = 1280;
		long byteSize = 12345L;
		SizedElement initial = new SizedElement(mime, height, width, byteSize);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes))
		{
			output.writeObject(initial);
		}
		SizedElement result = null;
		try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
		{
			result = (SizedElement) input.readObject();
		}
		Assert.assertEquals(mime, result.mime());
		Assert.assertEquals(height, result.height());
		Assert.assertEquals(width, result.width());
		Assert.assertEquals(byteSize, result.byteSize());
	}

	@Test
	public void testDraftSerialization() throws Throwable
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
		Draft initial = new Draft(id, publishedSecondsUtc, title, description, discussionUrl, thumbnail, null, null);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes))
		{
			output.writeObject(initial);
		}
		Draft result = null;
		try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
		{
			result = (Draft) input.readObject();
		}
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
		Draft initial = new Draft(id, publishedSecondsUtc, title, description, discussionUrl, thumbnail, null, null);
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
}
