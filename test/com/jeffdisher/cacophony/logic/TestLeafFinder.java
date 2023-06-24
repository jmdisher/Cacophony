package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestLeafFinder
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] {3});

	@Test
	public void testNone() throws Throwable
	{
		AbstractRecord record = _emptyRecord();
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
		AbstractRecord record = _emptyRecord();
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
		AbstractRecord record = _emptyRecord();
		_add(record, F1, "image/jpeg", null, true);
		_add(record, F2, "video/webm", new int[] { 640, 480 }, false);
		_add(record, F3, "audio/ogg", new int[] { 0, 0 }, false);
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
		AbstractRecord record = _emptyRecord();
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
		AbstractRecord record = _emptyRecord();
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
		AbstractRecord record = _emptyRecord();
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


	private static AbstractRecord _emptyRecord()
	{
		AbstractRecord record = AbstractRecord.createNew();
		record.setName("name");
		record.setDescription("Description");
		record.setPublisherKey(MockKeys.K1);
		record.setPublishedSecondsUtc(1L);
		return record;
	}

	private static void _add(AbstractRecord record, IpfsFile cid, String mime, int[] dimensions, boolean special)
	{
		if (special)
		{
			record.setThumbnail(mime, cid);
		}
		else
		{
			List<AbstractRecord.Leaf> leaves = record.getVideoExtension();
			List<AbstractRecord.Leaf> newLeaves = new ArrayList<>();
			if (null != leaves)
			{
				newLeaves.addAll(leaves);
			}
			AbstractRecord.Leaf leaf = new AbstractRecord.Leaf(cid, mime, dimensions[0], dimensions[1]);
			newLeaves.add(leaf);
			record.setVideoExtension(newLeaves);
		}
	}

	private static AbstractRecord _verify(AbstractRecord record) throws Throwable
	{
		byte[] data = record.serializeV1();
		return AbstractRecord.DESERIALIZER.apply(data);
	}
}
