package com.jeffdisher.cacophony.logic;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestLeafFinder
{
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final IpfsFile F3 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCF");

	@Test
	public void testNone() throws Throwable
	{
		StreamRecord record = _emptyRecord();
		record = _verify(record);
		LeafFinder finder = LeafFinder.parseRecord(record);
		Assert.assertNull(finder.thumbnail);
		Assert.assertNull(finder.audio);
		Assert.assertEquals(0, finder.sortedVideos.length);
		Assert.assertNull(finder.largestVideoWithLimit(1000));
	}

	@Test
	public void testThumbOneVideo() throws Throwable
	{
		StreamRecord record = _emptyRecord();
		_add(record, F1, "image/jpeg", null, true);
		_add(record, F2, "video/webm", new int[] { 640, 480 }, false);
		record = _verify(record);
		LeafFinder finder = LeafFinder.parseRecord(record);
		Assert.assertEquals(F1, finder.thumbnail);
		Assert.assertNull(finder.audio);
		Assert.assertEquals(1, finder.sortedVideos.length);
		Assert.assertEquals(640, finder.sortedVideos[0].edgeSize());
		Assert.assertEquals(finder.sortedVideos[0], finder.largestVideoWithLimit(1000));
		Assert.assertNull(finder.largestVideoWithLimit(500));
	}

	@Test
	public void testOneOfEach() throws Throwable
	{
		StreamRecord record = _emptyRecord();
		_add(record, F1, "image/jpeg", null, true);
		_add(record, F2, "video/webm", new int[] { 640, 480 }, false);
		_add(record, F3, "audio/ogg", null, false);
		record = _verify(record);
		LeafFinder finder = LeafFinder.parseRecord(record);
		Assert.assertEquals(F1, finder.thumbnail);
		Assert.assertEquals(F3, finder.audio);
		Assert.assertEquals(1, finder.sortedVideos.length);
		Assert.assertEquals(640, finder.sortedVideos[0].edgeSize());
		Assert.assertEquals(finder.sortedVideos[0], finder.largestVideoWithLimit(1000));
		Assert.assertNull(finder.largestVideoWithLimit(500));
	}

	@Test
	public void testTwoVideos() throws Throwable
	{
		StreamRecord record = _emptyRecord();
		_add(record, F1, "video/webm", new int[] { 640, 480 }, false);
		_add(record, F2, "video/webm", new int[] { 1280, 720 }, false);
		record = _verify(record);
		LeafFinder finder = LeafFinder.parseRecord(record);
		Assert.assertNull(finder.thumbnail);
		Assert.assertNull(finder.audio);
		Assert.assertEquals(2, finder.sortedVideos.length);
		Assert.assertEquals(640, finder.sortedVideos[0].edgeSize());
		Assert.assertEquals(1280, finder.sortedVideos[1].edgeSize());
		Assert.assertEquals(finder.sortedVideos[1], finder.largestVideoWithLimit(2000));
		Assert.assertEquals(finder.sortedVideos[0], finder.largestVideoWithLimit(1000));
		Assert.assertNull(finder.largestVideoWithLimit(500));
	}

	@Test
	public void testTwoVideosReverse() throws Throwable
	{
		StreamRecord record = _emptyRecord();
		_add(record, F1, "video/webm", new int[] { 1280, 720 }, false);
		_add(record, F2, "video/webm", new int[] { 640, 480 }, false);
		record = _verify(record);
		LeafFinder finder = LeafFinder.parseRecord(record);
		Assert.assertNull(finder.thumbnail);
		Assert.assertNull(finder.audio);
		Assert.assertEquals(2, finder.sortedVideos.length);
		Assert.assertEquals(640, finder.sortedVideos[0].edgeSize());
		Assert.assertEquals(1280, finder.sortedVideos[1].edgeSize());
		Assert.assertEquals(finder.sortedVideos[1], finder.largestVideoWithLimit(2000));
		Assert.assertEquals(finder.sortedVideos[0], finder.largestVideoWithLimit(1000));
		Assert.assertNull(finder.largestVideoWithLimit(500));
	}

	@Test
	public void testTwoVideosSameSize() throws Throwable
	{
		StreamRecord record = _emptyRecord();
		_add(record, F1, "video/webm", new int[] { 640, 480 }, false);
		_add(record, F2, "video/mp4", new int[] { 640, 480 }, false);
		record = _verify(record);
		LeafFinder finder = LeafFinder.parseRecord(record);
		Assert.assertNull(finder.thumbnail);
		Assert.assertNull(finder.audio);
		Assert.assertEquals(2, finder.sortedVideos.length);
		Assert.assertEquals(640, finder.sortedVideos[0].edgeSize());
		Assert.assertEquals(640, finder.sortedVideos[1].edgeSize());
		Assert.assertEquals(finder.sortedVideos[1], finder.largestVideoWithLimit(640));
		Assert.assertNull(finder.largestVideoWithLimit(500));
	}


	private static StreamRecord _emptyRecord()
	{
		StreamRecord record = new StreamRecord();
		record.setName("name");
		record.setDescription("Description");
		record.setElements(new DataArray());
		record.setPublisherKey(MockKeys.K1.toPublicKey());
		record.setPublishedSecondsUtc(1L);
		return record;
	}

	private static void _add(StreamRecord record, IpfsFile cid, String mime, int[] dimensions, boolean special)
	{
		DataElement element = new DataElement();
		element.setCid(cid.toSafeString());
		element.setMime(mime);
		if (null != dimensions)
		{
			element.setHeight(dimensions[0]);
			element.setWidth(dimensions[1]);
		}
		if (special)
		{
			element.setSpecial(ElementSpecialType.IMAGE);
		}
		record.getElements().getElement().add(element);
	}

	private static StreamRecord _verify(StreamRecord record) throws Throwable
	{
		return GlobalData.deserializeRecord(GlobalData.serializeRecord(record));
	}
}
