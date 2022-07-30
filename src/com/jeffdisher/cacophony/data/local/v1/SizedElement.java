package com.jeffdisher.cacophony.data.local.v1;

import java.io.Serializable;

import org.junit.Assert;

import com.eclipsesource.json.JsonObject;


/**
 * SizedElement is used to serialize a description of out-of-line data which has a visual representation.  This
 * typically includes images and videos.
 * It never exists on its own, just being a sub-structure of Draft.
 * As it needs to be sent over the wire to the web interface, is can be converted to/from JSON.
 */
public record SizedElement(String mime
		, int height
		, int width
		, long byteSize
) implements Serializable
{
	public static SizedElement fromJson(JsonObject json)
	{
		// Note that these are allowed to be null.
		SizedElement element = null;
		if (null != json)
		{
			// We just assert that this is well-formed, to make the rest of the stack easier to debug.
			String mime = json.getString("mime", null);
			Assert.assertTrue(null != mime);
			int height = json.getInt("height", -1);
			Assert.assertTrue(height > 0);
			int width = json.getInt("width", -1);
			Assert.assertTrue(width > 0);
			long byteSize = json.getLong("byteSize", -1L);
			Assert.assertTrue(byteSize >= 0);
			element = new SizedElement(mime, height, width, byteSize);
		}
		return element;
	}

	public JsonObject toJson()
	{
		return new JsonObject()
			.add("mime", mime)
			.add("height", height)
			.add("width", width)
			.add("byteSize", byteSize)
		;
	}
}
