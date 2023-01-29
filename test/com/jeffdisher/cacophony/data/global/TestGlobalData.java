package com.jeffdisher.cacophony.data.global;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.types.FailedDeserializationException;


public class TestGlobalData {
	@Test
	public void testIndex1() throws IOException {
		StreamIndex index = new StreamIndex();
		index.setDescription("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setRecommendations("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setRecords("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setVersion(5);
		byte[] serialized = GlobalData.serializeIndex(index);
		String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<index xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/index.xsd\">\n"
				+ "    <version>5</version>\n"
				+ "    <description>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</description>\n"
				+ "    <recommendations>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</recommendations>\n"
				+ "    <records>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</records>\n"
				+ "</index>\n";
		Assert.assertEquals(expected, new String(serialized));
	}

	@Test
	public void testIndex2() throws FailedDeserializationException
	{
		byte[] input = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><index xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/index.xsd\"><version>5</version><description>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</description><recommendations>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</recommendations><records>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</records></index>".getBytes();
		StreamIndex didRead = GlobalData.deserializeIndex(input);
		Assert.assertEquals("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG", didRead.getDescription());
	}

	@Test
	public void testRecords1() throws IOException {
		StreamRecords data = new StreamRecords();
		data.getRecord().add("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		byte[] serialized = GlobalData.serializeRecords(data);
		String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<records xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/records.xsd\">\n"
				+ "    <record>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</record>\n"
				+ "</records>\n";
		Assert.assertEquals(expected, new String(serialized));
	}

	@Test
	public void testRecords2() throws FailedDeserializationException
	{
		byte[] input = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<records xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/records.xsd\">\n"
				+ "    <record>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</record>\n"
				+ "</records>\n").getBytes();
		StreamRecords readRecords = GlobalData.deserializeRecords(input);
		Assert.assertEquals("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG", readRecords.getRecord().get(0));
	}

	@Test
	public void testRecord1() throws IOException {
		StreamRecord record = new StreamRecord();
		record.setDiscussion("URL");
		record.setPublisherKey("public key goes here");
		record.setName("name");
		record.setDescription("description");
		record.setPublishedSecondsUtc(555);
		DataArray eltArray = new DataArray();
		record.setElements(eltArray);
		DataElement element = new DataElement();
		eltArray.getElement().add(element);
		element.setCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		element.setMime("mim");
		byte[] serialized = GlobalData.serializeRecord(record);
		String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<record xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/record.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <description>description</description>\n"
				+ "    <publishedSecondsUtc>555</publishedSecondsUtc>\n"
				+ "    <discussion>URL</discussion>\n"
				+ "    <elements>\n"
				+ "        <element>\n"
				+ "            <cid>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</cid>\n"
				+ "            <mime>mim</mime>\n"
				+ "        </element>\n"
				+ "    </elements>\n"
				+ "    <publisherKey>public key goes here</publisherKey>\n"
				+ "</record>\n";
		Assert.assertEquals(expected, new String(serialized));
	}

	@Test
	public void testRecord2() throws FailedDeserializationException
	{
		byte[] input = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<record xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/record.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <description>description</description>\n"
				+ "    <publishedSecondsUtc>555</publishedSecondsUtc>\n"
				+ "    <discussion>URL</discussion>\n"
				+ "    <elements>\n"
				+ "        <element>\n"
				+ "            <cid>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</cid>\n"
				+ "            <mime>mim</mime>\n"
				+ "        </element>\n"
				+ "    </elements>\n"
				+ "    <publisherKey>public key of the publisher (so they can be found just from this record)</publisherKey>\n"
				+ "</record>\n").getBytes();
		StreamRecord readRecord = GlobalData.deserializeRecord(input);
		Assert.assertEquals("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG", readRecord.getElements().getElement().get(0).getCid());
	}

	@Test
	public void testDescription1() throws IOException {
		StreamDescription data = new StreamDescription();
		data.setDescription("description");
		data.setName("name");
		data.setPicture("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		byte[] serialized = GlobalData.serializeDescription(data);
		String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<description xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/description.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <description>description</description>\n"
				+ "    <picture>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</picture>\n"
				+ "</description>\n";
		Assert.assertEquals(expected, new String(serialized));
	}

	@Test
	public void testDescription2() throws FailedDeserializationException
	{
		byte[] input = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<description xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/description.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <description>description</description>\n"
				+ "    <picture>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</picture>\n"
				+ "</description>\n").getBytes();
		StreamDescription didRead = GlobalData.deserializeDescription(input);
		Assert.assertEquals("description", didRead.getDescription());
	}

	@Test
	public void testRecommendations1() throws IOException {
		StreamRecommendations data = new StreamRecommendations();
		data.getUser().add("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");
		byte[] serialized = GlobalData.serializeRecommendations(data);
		String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<recommendations xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/recommendations.xsd\">\n"
				+ "    <user>z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141</user>\n"
				+ "</recommendations>\n";
		Assert.assertEquals(expected, new String(serialized));
	}

	@Test
	public void testRecommendations2() throws FailedDeserializationException
	{
		byte[] input = ("<recommendations xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/recommendations.xsd\">\n"
				+ "    <user>z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141</user>\n"
				+ "</recommendations>\n").getBytes();
		StreamRecommendations didRead = GlobalData.deserializeRecommendations(input);
		Assert.assertEquals("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141", didRead.getUser().get(0));
	}

	@Test
	public void testPublishWithImage() throws FailedDeserializationException
	{
		DataElement element = new DataElement();
		element.setCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		element.setMime("image/jpeg");
		element.setSpecial(ElementSpecialType.IMAGE);
		DataArray array = new DataArray();
		array.getElement().add(element);
		StreamRecord record = new StreamRecord();
		record.setName("name");
		record.setDescription("descriptoin");
		record.setElements(array);
		record.setPublisherKey("public key");
		record.setPublishedSecondsUtc(12345L);
		byte[] rawRecord = GlobalData.serializeRecord(record);
		StreamRecord didRead = GlobalData.deserializeRecord(rawRecord);
		Assert.assertEquals("image", ((DataElement)(didRead.getElements().getElement().get(0))).getSpecial().value());
	}
}
