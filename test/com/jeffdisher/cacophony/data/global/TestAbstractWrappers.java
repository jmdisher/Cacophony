package com.jeffdisher.cacophony.data.global;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;


public class TestAbstractWrappers
{
	@Test
	public void emptyRecord() throws Throwable
	{
		// We need to set some minimal values for the encoding to work.
		StreamRecord record = new StreamRecord();
		record.setName("name");
		record.setDescription("description");
		record.setPublishedSecondsUtc(1L);
		record.setPublisherKey(MockKeys.K0.toPublicKey());
		record.setElements(new DataArray());
		byte[] data = GlobalData.serializeRecord(record);
		AbstractRecord middle = AbstractRecord.DESERIALIZER.apply(data);
		byte[] data2 = middle.serializeV1();
		Assert.assertArrayEquals(data, data2);
		
		Assert.assertEquals("name", middle.getName());
		Assert.assertEquals("description", middle.getDescription());
		Assert.assertEquals(1L, middle.getPublishedSecondsUtc());
		Assert.assertEquals(MockKeys.K0, middle.getPublisherKey());
		Assert.assertNull(middle.getDiscussionUrl());
		Assert.assertNull(middle.getThumbnailMime());
		Assert.assertEquals(0, middle.getExternalElementCount());
		Assert.assertNull(middle.getVideoExtension());
	}

	@Test
	public void fullRecord() throws Throwable
	{
		DataElement thumbnail = new DataElement();
		thumbnail.setCid(MockSingleNode.generateHash(new byte[] { 1 }).toSafeString());
		thumbnail.setMime("image/jpeg");
		thumbnail.setSpecial(ElementSpecialType.IMAGE);
		DataElement video = new DataElement();
		video.setCid(MockSingleNode.generateHash(new byte[] { 2 }).toSafeString());
		video.setMime("video/webm");
		video.setWidth(1);
		video.setHeight(1);
		DataElement audio = new DataElement();
		audio.setCid(MockSingleNode.generateHash(new byte[] { 3 }).toSafeString());
		audio.setMime("audio/ogg");
		audio.setWidth(0);
		audio.setHeight(0);
		DataArray elements = new DataArray();
		elements.getElement().add(thumbnail);
		elements.getElement().add(video);
		elements.getElement().add(audio);
		StreamRecord record = new StreamRecord();
		record.setName("name");
		record.setDescription("description");
		record.setPublishedSecondsUtc(1L);
		record.setPublisherKey(MockKeys.K0.toPublicKey());
		record.setDiscussion("http://example.com");
		record.setElements(elements);
		byte[] data = GlobalData.serializeRecord(record);
		AbstractRecord middle = AbstractRecord.DESERIALIZER.apply(data);
		byte[] data2 = middle.serializeV1();
		Assert.assertArrayEquals(data, data2);
		
		Assert.assertEquals("name", middle.getName());
		Assert.assertEquals("description", middle.getDescription());
		Assert.assertEquals(1L, middle.getPublishedSecondsUtc());
		Assert.assertEquals(MockKeys.K0, middle.getPublisherKey());
		Assert.assertEquals("http://example.com", middle.getDiscussionUrl());
		Assert.assertEquals("image/jpeg", middle.getThumbnailMime());
		Assert.assertEquals(MockSingleNode.generateHash(new byte[] { 1 }), middle.getThumbnailCid());
		Assert.assertEquals(3, middle.getExternalElementCount());
		Assert.assertEquals(2, middle.getVideoExtension().size());
	}

	@Test
	public void emptyRecords() throws Throwable
	{
		StreamRecords record = new StreamRecords();
		byte[] data = GlobalData.serializeRecords(record);
		AbstractRecords middle = AbstractRecords.DESERIALIZER.apply(data);
		byte[] data2 = middle.serializeV1();
		Assert.assertArrayEquals(data, data2);
	}

	@Test
	public void smallRecords() throws Throwable
	{
		StreamRecords records = new StreamRecords();
		records.getRecord().add(MockSingleNode.generateHash(new byte[] { 1 }).toSafeString());
		byte[] data = GlobalData.serializeRecords(records);
		AbstractRecords middle = AbstractRecords.DESERIALIZER.apply(data);
		byte[] data2 = middle.serializeV1();
		Assert.assertArrayEquals(data, data2);
		
		Assert.assertEquals(1, middle.getRecordList().size());
		Assert.assertEquals(MockSingleNode.generateHash(new byte[] { 1 }), middle.getRecordList().get(0));
	}
}
