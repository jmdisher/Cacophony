package com.jeffdisher.cacophony.data.global;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;


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
	public static final long SIZE_LIMIT_BYTES = SizeLimits.MAX_RECORD_SIZE_BYTES;
	public static final DataDeserializer<AbstractRecord> DESERIALIZER = (byte[] data) -> _convertRecord(GlobalData.deserializeRecord(data)); 

	/**
	 * @return A new empty record.
	 */
	public static AbstractRecord createNew()
	{
		return new AbstractRecord(null
				, null
				, null
				, null
				, 0L
				, null
				, null
				, null
		);
	}


	private static AbstractRecord _convertRecord(StreamRecord recordV1)
	{
		List<Leaf> splitArray = new ArrayList<>();
		IpfsFile thumbnailCid = _splitAttachments(splitArray, recordV1);
		// We just assume that the MIME is JPEG.
		String thumbnailMime = (null != thumbnailCid) ? "image/jpeg" : null;
		return new AbstractRecord(recordV1.getName()
				, recordV1.getDescription()
				, recordV1.getDiscussion()
				, IpfsKey.fromPublicKey(recordV1.getPublisherKey())
				, recordV1.getPublishedSecondsUtc()
				, thumbnailMime
				, thumbnailCid
				, (splitArray.isEmpty() ? null : splitArray)
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


	private String _name;
	private String _description;
	private String _discussionUrl;
	private IpfsKey _publisherKey;
	private long _publishedSecondsUtc;
	private String _thumbnailMime;
	private IpfsFile _thumbnailCid;
	private List<Leaf> _leaves;

	private AbstractRecord(String name
			, String description
			, String discussionUrl
			, IpfsKey publisherKey
			, long publishedSecondsUtc
			, String thumbnailMime
			, IpfsFile thumbnailCid
			, List<Leaf> leaves
	)
	{
		Assert.assertTrue((null == thumbnailMime) == (null == thumbnailCid));
		
		_name = name;
		_description = description;
		_discussionUrl = discussionUrl;
		_publisherKey = publisherKey;
		_publishedSecondsUtc = publishedSecondsUtc;
		_thumbnailMime = thumbnailMime;
		_thumbnailCid = thumbnailCid;
		_leaves = leaves;
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
		Assert.assertTrue(!name.isEmpty());
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
	 * @param description The new description (cannot be null).
	 */
	public void setDescription(String description)
	{
		Assert.assertTrue(null != description);
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
		StreamRecord record = new StreamRecord();
		Assert.assertTrue(_name.length() > 0);
		record.setName(_name);
		Assert.assertTrue(null != _description);
		record.setDescription(_description);
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
	 * The data related to a single attachment in the video extension.
	 */
	public static record Leaf(IpfsFile cid
			, String mime
			, int height
			, int width
	) {}
}
