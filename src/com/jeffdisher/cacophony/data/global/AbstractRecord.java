package com.jeffdisher.cacophony.data.global;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.data.global.v2.extensions.CacophonyExtensionVideo;
import com.jeffdisher.cacophony.data.global.v2.extensions.VideoFormat;
import com.jeffdisher.cacophony.data.global.v2.record.CacophonyRecord;
import com.jeffdisher.cacophony.data.global.v2.record.ThumbnailReference;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;
import com.jeffdisher.cacophony.utils.SizeLimits2;


/**
 * Given that both StreamRecord (v1) and CacophonyRecord (v2) have many small facets and are bound to lower-level types
 * (strings in cases where we would ideally use IpfsFile or IpfsKey, for example), this abstract implementation has been
 * produced for higher-level simplicity.
 * Additionally, it is intended to handle the support for bimodal version reading, such that other parts of the system
 * don't need to concern themselves with the differences between version 1 and 2 of the on-IPFS data model.
 * This higher-level representation allows for the calling code to not deal with the changes to the physical encoding
 * while also allowing for a somewhat higher-level interface.
 */
public class AbstractRecord
{
	public static final long SIZE_LIMIT_BYTES = SizeLimits2.MAX_RECORD_SIZE_BYTES;
	public static final DataDeserializer<AbstractRecord> DESERIALIZER = (byte[] data) -> _commonMultiVersionLoad(data);

	static {
		// We must make sure that these sizes are the same.
		Assert.assertTrue(SizeLimits2.MAX_RECORD_SIZE_BYTES == SizeLimits.MAX_RECORD_SIZE_BYTES);
	}

	/**
	 * @return A new empty record.
	 */
	public static AbstractRecord createNew()
	{
		return new AbstractRecord(0
				, null
				, null
				, null
				, null
				, 0L
				, null
				, null
				, null
				, null
		);
	}

	public static boolean validateName(String name)
	{
		// Check the bounds as described in record2.xsd.
		return ((null != name) && (name.length() >= 1) && (name.length() <= 255));
	}

	public static boolean validateDescription(String description)
	{
		// Check the bounds as described in record2.xsd.
		return ((null == description) || ((description.length() >= 1) && (description.length() <= 32768)));
	}


	private static AbstractRecord _commonMultiVersionLoad(byte[] data) throws FailedDeserializationException
	{
		AbstractRecord converted;
		try
		{
			// We check for version 2, first.
			CacophonyExtendedRecord recordV2 = GlobalData2.deserializeRecord(data);
			converted = _convertRecordV2(recordV2);
		}
		catch (FailedDeserializationException e)
		{
			// We will try version 1.
			StreamRecord recordV1 = GlobalData.deserializeRecord(data);
			converted = _convertRecordV1(recordV1);
		}
		
		// We would have loaded one of them or thrown.
		return converted;
	}

	private static AbstractRecord _convertRecordV1(StreamRecord recordV1)
	{
		List<Leaf> splitArray = new ArrayList<>();
		IpfsFile thumbnailCid = _splitAttachments(splitArray, recordV1);
		// We just assume that the MIME is JPEG.
		String thumbnailMime = (null != thumbnailCid) ? "image/jpeg" : null;
		return new AbstractRecord(1
				, recordV1.getName()
				, recordV1.getDescription()
				, recordV1.getDiscussion()
				, IpfsKey.fromPublicKey(recordV1.getPublisherKey())
				, recordV1.getPublishedSecondsUtc()
				, thumbnailMime
				, thumbnailCid
				, null
				, (splitArray.isEmpty() ? null : splitArray)
		);
	}

	private static AbstractRecord _convertRecordV2(CacophonyExtendedRecord recordV2) throws FailedDeserializationException
	{
		CacophonyRecord record = recordV2.record();
		CacophonyExtensionVideo video = recordV2.video();
		
		// The record must exist but the video extension can be null.
		Assert.assertTrue(null != record);
		ThumbnailReference thumb = record.getThumbnail();
		String thumbnailMime = null;
		IpfsFile thumbnailCid = null;
		if (null != thumb)
		{
			thumbnailMime = thumb.getMime();
			// We want to make sure that this is a valid CID.
			thumbnailCid = IpfsFile.fromIpfsCid(thumb.getValue());
			if (null == thumbnailCid)
			{
				throw new FailedDeserializationException(ThumbnailReference.class);
			}
		}
		
		IpfsFile replyToRecordCid = (null != record.getReplyTo())
				? IpfsFile.fromIpfsCid(record.getReplyTo())
				: null
		;
		List<Leaf> videoArray = null;
		if (null != video)
		{
			videoArray = new ArrayList<>();
			for (VideoFormat format : video.getFormat())
			{
				IpfsFile cid = IpfsFile.fromIpfsCid(format.getCid());
				if (null == cid)
				{
					throw new FailedDeserializationException(VideoFormat.class);
				}
				videoArray.add(new Leaf(cid
						, format.getMime()
						, format.getHeight()
						, format.getWidth()
				));
			}
			// We would have failed to parse if this were empty.
			Assert.assertTrue(!videoArray.isEmpty());
		}
		
		return new AbstractRecord(2
				, record.getName()
				, record.getDescription()
				, record.getDiscussionUrl()
				, IpfsKey.fromPublicKey(record.getPublisherKey())
				, record.getPublishedSecondsUtc()
				, thumbnailMime
				, thumbnailCid
				, replyToRecordCid
				, videoArray
		);
	}

	private static IpfsFile _splitAttachments(List<Leaf> videoArray, StreamRecord recordV1)
	{
		IpfsFile thumbnailCid = null;
		List<DataElement> input = recordV1.getElements().getElement();
		for (DataElement elt : input)
		{
			if (ElementSpecialType.IMAGE.equals(elt.getSpecial()))
			{
				Assert.assertTrue(null == thumbnailCid);
				thumbnailCid = IpfsFile.fromIpfsCid(elt.getCid());
			}
			else
			{
				int height = (null != elt.getHeight()) ? elt.getHeight() : 0;
				int width = (null != elt.getWidth()) ? elt.getWidth() : 0;
				videoArray.add(new Leaf(IpfsFile.fromIpfsCid(elt.getCid())
						, elt.getMime()
						, height
						, width
				));
			}
		}
		return thumbnailCid;
	}


	private int _version;
	private String _name;
	private String _description;
	private String _discussionUrl;
	private IpfsKey _publisherKey;
	private long _publishedSecondsUtc;
	private String _thumbnailMime;
	private IpfsFile _thumbnailCid;
	private IpfsFile _replyToRecord;
	private List<Leaf> _leaves;

	private AbstractRecord(int version
			, String name
			, String description
			, String discussionUrl
			, IpfsKey publisherKey
			, long publishedSecondsUtc
			, String thumbnailMime
			, IpfsFile thumbnailCid
			, IpfsFile replyToRecord
			, List<Leaf> leaves
	)
	{
		Assert.assertTrue((null == thumbnailMime) == (null == thumbnailCid));
		
		_version = version;
		_name = name;
		_description = description;
		_discussionUrl = discussionUrl;
		_publisherKey = publisherKey;
		_publishedSecondsUtc = publishedSecondsUtc;
		_thumbnailMime = thumbnailMime;
		_thumbnailCid = thumbnailCid;
		_replyToRecord = replyToRecord;
		_leaves = leaves;
	}

	/**
	 * @return The version number of the serialization found/used (0 if not loaded or saved, yet).
	 */
	public int getVersion()
	{
		return _version;
	}

	/**
	 * @return The name of the record.
	 */
	public String getName()
	{
		return _name;
	}

	/**
	 * @param name The new name (cannot be null or empty).
	 */
	public void setName(String name)
	{
		Assert.assertTrue(validateName(name));
		_name = name;
	}

	/**
	 * @return The description of the record.
	 */
	public String getDescription()
	{
		return _description;
	}

	/**
	 * @param description The new description (can be null but not empty).
	 */
	public void setDescription(String description)
	{
		Assert.assertTrue(validateDescription(description));
		_description = description;
	}

	/**
	 * @return The discussion URL from the record.
	 */
	public String getDiscussionUrl()
	{
		return _discussionUrl;
	}

	/**
	 * @param discussionUrl The new discussion URL (null for "none").
	 */
	public void setDiscussionUrl(String discussionUrl)
	{
		_discussionUrl = discussionUrl;
	}

	/**
	 * @return The publisher of the record.
	 */
	public IpfsKey getPublisherKey()
	{
		return _publisherKey;
	}

	/**
	 * @param publisherKey The new publisher (cannot be null).
	 */
	public void setPublisherKey(IpfsKey publisherKey)
	{
		Assert.assertTrue(null != publisherKey);
		_publisherKey = publisherKey;
	}

	/**
	 * @return The publish time in seconds since epoch in UTC.
	 */
	public long getPublishedSecondsUtc()
	{
		return _publishedSecondsUtc;
	}

	/**
	 * @param publishedSecondsUtc The new publish time (must be >0).
	 */
	public void setPublishedSecondsUtc(long publishedSecondsUtc)
	{
		Assert.assertTrue(publishedSecondsUtc > 0L);
		_publishedSecondsUtc = publishedSecondsUtc;
	}

	/**
	 * @return The mime type of the thumbnail (null if there isn't one).
	 */
	public String getThumbnailMime()
	{
		return _thumbnailMime;
	}

	/**
	 * @return The CID of the thumbnail (null if there isn't one).
	 */
	public IpfsFile getThumbnailCid()
	{
		return _thumbnailCid;
	}

	/**
	 * Sets the parameters related to the thumbnail image.  These must either both be null or neither be null.
	 * 
	 * @param thumbnailMime The mime type of the thumbnail.
	 * @param thumbnailCid The new thumbnail CID.
	 */
	public void setThumbnail(String thumbnailMime, IpfsFile thumbnailCid)
	{
		Assert.assertTrue((null == thumbnailMime) == (null == thumbnailCid));
		_thumbnailMime = thumbnailMime;
		_thumbnailCid = thumbnailCid;
	}

	/**
	 * @return The CID of the record to which this is a response (could be null).
	 */
	public IpfsFile getReplyTo()
	{
		return _replyToRecord;
	}

	/**
	 * Sets the CID of the record to which this post is a response.
	 * 
	 * @param recordCid The CID of the record (could be null).
	 */
	public void setReplyTo(IpfsFile recordCid)
	{
		_replyToRecord = recordCid;
	}

	/**
	 * @return The list of elements in the video extension (null if there is no video extension).
	 */
	public List<Leaf> getVideoExtension()
	{
		return _leaves;
	}

	/**
	 * @param attachmentArray The new list of video extension elements (if non-null, cannot be empty).
	 */
	public void setVideoExtension(List<Leaf> attachmentArray)
	{
		Assert.assertTrue((null == attachmentArray) || !attachmentArray.isEmpty());
		_leaves = attachmentArray;
	}

	/**
	 * This is to synthesize the v1 behaviour where thumbnails, videos, and audio were all considered the same kind of
	 * leaves, so this returns a total of them (but no meta-data elements).
	 * 
	 * @return The total number of external data attachments.
	 */
	public int getExternalElementCount()
	{
		int count = 0;
		if (null != _thumbnailCid)
		{
			count += 1;
		}
		if (null != _leaves)
		{
			count += _leaves.size();
		}
		return count;
	}

	/**
	 * Serializes the instance as a V1 StreamRecord, returning the resulting byte array.
	 * 
	 * @return The byte array of the serialized instance
	 * @throws SizeConstraintException The instance was too big to fit within limits, once serialized.
	 */
	public byte[] serializeV1() throws SizeConstraintException
	{
		_version = 1;
		StreamRecord record = new StreamRecord();
		Assert.assertTrue(_name.length() > 0);
		record.setName(_name);
		// We will allow the description to be null, since that makes more sense for V2, but will then set it to be empty for V1 (since it requires description).
		if (null == _description)
		{
			record.setDescription("");
		}
		else
		{
			record.setDescription(_description);
		}
		// We would rather publish no discussion than an empty one.
		Assert.assertTrue((null == _discussionUrl) || !_discussionUrl.isEmpty());
		record.setDiscussion(_discussionUrl);
		Assert.assertTrue(_publishedSecondsUtc > 0L);
		record.setPublishedSecondsUtc(_publishedSecondsUtc);
		Assert.assertTrue(null != _publisherKey);
		record.setPublisherKey(_publisherKey.toPublicKey());
		DataArray array = new DataArray();
		if (null != _thumbnailCid)
		{
			DataElement elt = new DataElement();
			elt.setCid(_thumbnailCid.toSafeString());
			// We will use this hard-coded MIME since this is just an intermediate step.
			elt.setMime("image/jpeg");
			elt.setSpecial(ElementSpecialType.IMAGE);
			array.getElement().add(elt);
		}
		if (null != _leaves)
		{
			Assert.assertTrue(!_leaves.isEmpty());
			for (Leaf leaf : _leaves)
			{
				DataElement elt = new DataElement();
				elt.setCid(leaf.cid().toSafeString());
				elt.setMime(leaf.mime());
				elt.setHeight(leaf.height());
				elt.setWidth(leaf.width());
				array.getElement().add(elt);
			}
		}
		record.setElements(array);
		return GlobalData.serializeRecord(record);
	}

	/**
	 * Serializes the instance as a V2 CacophonyRecord (including CacophonyExtensionVideo, if applicable), returning the
	 * resulting byte array.
	 * 
	 * @return The byte array of the serialized instance
	 * @throws SizeConstraintException The instance was too big to fit within limits, once serialized.
	 */
	public byte[] serializeV2() throws SizeConstraintException
	{
		_version = 2;
		CacophonyRecord record = new CacophonyRecord();
		Assert.assertTrue(!_name.isEmpty());
		record.setName(_name);
		if (null != _description)
		{
			Assert.assertTrue(!_description.isEmpty());
			record.setDescription(_description);
		}
		Assert.assertTrue(_publishedSecondsUtc > 0L);
		record.setPublishedSecondsUtc(_publishedSecondsUtc);
		if (null != _discussionUrl)
		{
			Assert.assertTrue(!_discussionUrl.isEmpty());
			record.setDiscussionUrl(_discussionUrl);
		}
		Assert.assertTrue(null != _publisherKey);
		record.setPublisherKey(_publisherKey.toPublicKey());
		if (null != _replyToRecord)
		{
			record.setReplyTo(_replyToRecord.toSafeString());
		}
		if (null != _thumbnailCid)
		{
			Assert.assertTrue(!_thumbnailMime.isEmpty());
			ThumbnailReference ref = new ThumbnailReference();
			ref.setMime(_thumbnailMime);
			ref.setValue(_thumbnailCid.toSafeString());
			record.setThumbnail(ref);
		}
		CacophonyExtensionVideo extension = null;
		if (null != _leaves)
		{
			Assert.assertTrue(!_leaves.isEmpty());
			extension = new CacophonyExtensionVideo();
			for (Leaf leaf : _leaves)
			{
				VideoFormat video = new VideoFormat();
				video.setCid(leaf.cid.toSafeString());
				video.setHeight(leaf.height);
				video.setWidth(leaf.width);
				video.setMime(leaf.mime);
				extension.getFormat().add(video);
			}
		}
		return GlobalData2.serializeRecord(new CacophonyExtendedRecord(record, extension));
	}


	/**
	 * The data related to a single attachment in the video extension.
	 */
	public static record Leaf(IpfsFile cid
			, String mime
			, int height
			, int width
	) {}
}
