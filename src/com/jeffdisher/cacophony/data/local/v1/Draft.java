package com.jeffdisher.cacophony.data.local.v1;

import java.io.Serializable;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Draft represents a not-yet-published post to a Cacophony stream.
 * It uses Java serialization for on-disk storage and JSON for its wire encoding for the web interface.
 */
public record Draft(int id
		, long publishedSecondsUtc
		, String title
		, String description
		, String discussionUrl
		, SizedElement thumbnail
		, SizedElement originalVideo
		, SizedElement processedVideo
) implements Serializable
{
	public static Draft fromJson(JsonObject json)
	{
		// We just assert that this is well-formed, to make the rest of the stack easier to debug.
		int id = json.getInt("id", -1);
		Assert.assertTrue(id >= 0);
		long publishedSecondsUtc = json.getLong("publishedSecondsUtc", -1L);
		Assert.assertTrue(publishedSecondsUtc >= 0);
		String title = json.getString("title", null);
		Assert.assertTrue(null != title);
		String description = json.getString("description", null);
		Assert.assertTrue(null != description);
		String discussionUrl = json.getString("discussionUrl", null);
		SizedElement thumbnail = _getElementOrNull(json, "thumbnail");
		SizedElement originalVideo = _getElementOrNull(json, "originalVideo");
		SizedElement processedVideo = _getElementOrNull(json, "processedVideo");
		return new Draft(id, publishedSecondsUtc, title, description, discussionUrl, thumbnail, originalVideo, processedVideo);
	}

	private static SizedElement _getElementOrNull(JsonObject json, String key)
	{
		JsonValue val = json.get(key);
		JsonObject object = (val.isNull())
				? null
				: (JsonObject) val
		;
		return SizedElement.fromJson(object);
	}


	public JsonObject toJson()
	{
		return new JsonObject()
			.add("id", id)
			.add("publishedSecondsUtc", publishedSecondsUtc)
			.add("title", title)
			.add("description", description)
			.add("thumbnail", (null != thumbnail) ? thumbnail.toJson() : Json.NULL)
			.add("discussionUrl", (null != discussionUrl) ? Json.value(discussionUrl) : Json.NULL)
			.add("originalVideo", (null != originalVideo) ? originalVideo.toJson() : Json.NULL)
			.add("processedVideo", (null != processedVideo) ? processedVideo.toJson() : Json.NULL)
		;
	}
}
