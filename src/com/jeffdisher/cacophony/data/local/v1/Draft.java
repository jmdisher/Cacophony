package com.jeffdisher.cacophony.data.local.v1;

import java.io.Serializable;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.utils.Assert;


public record Draft(int id
		, long publishedSecondsUtc
		, String title
		, String description
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
		SizedElement thumbnail = SizedElement.fromJson((JsonObject) json.get("thumbnail"));
		SizedElement originalVideo = SizedElement.fromJson((JsonObject) json.get("originalVideo"));
		SizedElement processedVideo = SizedElement.fromJson((JsonObject) json.get("processedVideo"));
		return new Draft(id, publishedSecondsUtc, title, description, thumbnail, originalVideo, processedVideo);
	}

	public JsonObject toJson()
	{
		return new JsonObject()
			.add("id", id)
			.add("publishedSecondsUtc", publishedSecondsUtc)
			.add("title", title)
			.add("description", description)
			.add("thumbnail", (null != thumbnail) ? thumbnail.toJson() : Json.NULL)
			.add("originalVideo", (null != originalVideo) ? originalVideo.toJson() : Json.NULL)
			.add("processedVideo", (null != processedVideo) ? processedVideo.toJson() : Json.NULL)
		;
	}
}
