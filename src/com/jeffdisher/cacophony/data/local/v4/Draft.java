package com.jeffdisher.cacophony.data.local.v4;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Draft represents a not-yet-published post to a Cacophony stream.
 * It uses JSON for on-disk and over-the-wire encoding but the actual IO and serialization is handled elsewhere.
 * Note that the  title, description, and discussionUrl are never null but _can_ be empty strings (which, in the case of
 * discussionUrl, means "no link").
 */
public record Draft(int id
		, long publishedSecondsUtc
		, String title
		, String description
		, String discussionUrl
		, SizedElement thumbnail
		, SizedElement originalVideo
		, SizedElement processedVideo
		, SizedElement audio
		, IpfsFile replyTo
)
{
	/**
	 * Creates an instance from a JSON representation, typically loaded from disk.
	 * 
	 * @param json The JSON object.
	 * @return The new Draft instance.
	 */
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
		Assert.assertTrue(null != discussionUrl);
		SizedElement thumbnail = _getElementOrNull(json, "thumbnail");
		SizedElement originalVideo = _getElementOrNull(json, "originalVideo");
		SizedElement processedVideo = _getElementOrNull(json, "processedVideo");
		SizedElement audio = _getElementOrNull(json, "audio");
		JsonValue rawReplyTo = json.get("replyTo");
		// Older versions may be missing the replyTo and we will store this as a null, either way, if not set.
		IpfsFile replyTo = ((null == rawReplyTo) || rawReplyTo.isNull())
				? null
				: IpfsFile.fromIpfsCid(rawReplyTo.asString())
		;
		return new Draft(id, publishedSecondsUtc, title, description, discussionUrl, thumbnail, originalVideo, processedVideo, audio, replyTo);
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


	/**
	 * @return The receiver, encoded as a JSON object.
	 */
	public JsonObject toJson()
	{
		return new JsonObject()
			.add("id", id)
			.add("publishedSecondsUtc", publishedSecondsUtc)
			.add("title", title)
			.add("description", description)
			.add("thumbnail", (null != thumbnail) ? thumbnail.toJson() : Json.NULL)
			// We will manually ensure that the URL is non-null since some older data models may still exist with a null value.
			.add("discussionUrl", Json.value((null != discussionUrl) ? discussionUrl : ""))
			.add("originalVideo", (null != originalVideo) ? originalVideo.toJson() : Json.NULL)
			.add("processedVideo", (null != processedVideo) ? processedVideo.toJson() : Json.NULL)
			.add("audio", (null != audio) ? audio.toJson() : Json.NULL)
			.add("replyTo", (null != replyTo) ? Json.value(replyTo.toSafeString()) : Json.NULL)
		;
	}
}
