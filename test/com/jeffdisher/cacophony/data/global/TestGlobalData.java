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


public class TestGlobalData {
	@Test
	public void testIndex() throws IOException {
		StreamIndex index = new StreamIndex();
		index.setDescription("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setRecommendations("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setRecords("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setVersion(5);
		byte[] serialized = GlobalData.serializeIndex(index);
		System.out.write(serialized);
		byte[] input = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><index xmlns=\"http://jeffdisher.com/cacophony/index.xsd\"><version>5</version><description>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</description><recommendations>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</recommendations><records>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</records></index>".getBytes();
		StreamIndex didRead = GlobalData.deserializeIndex(input);
		System.out.println(didRead.getRecords());
	}

	@Test
	public void testRecords() throws IOException {
		StreamRecords data = new StreamRecords();
		data.getRecord().add("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		byte[] serialized = GlobalData.serializeRecords(data);
		System.out.write(serialized);
		
		byte[] input = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<records xmlns=\"http://jeffdisher.com/cacophony/records.xsd\">\n"
				+ "    <record>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</record>\n"
				+ "</records>\n").getBytes();
		StreamRecords readRecords = GlobalData.deserializeRecords(input);
		System.out.println(readRecords.getRecord().get(0));
	}

	@Test
	public void testRecord() throws IOException {
		StreamRecord record = new StreamRecord();
		record.setDiscussion("URL");
		record.setPublisherKey("public key goes here");
		record.setName("name");
		record.setPublishedSecondsUtc(555);
		DataArray eltArray = new DataArray();
		record.setElements(eltArray);
		DataElement element = new DataElement();
		eltArray.getElement().add(element);
		element.setCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		element.setMime("mim");
		byte[] serialized = GlobalData.serializeRecord(record);
		System.out.write(serialized);
		
		byte[] input = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<record xmlns=\"http://jeffdisher.com/cacophony/record.xsd\">\n"
				+ "    <name>name</name>\n"
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
		System.out.println(readRecord.getName());
	}

	@Test
	public void testDescription() throws IOException {
		StreamDescription data = new StreamDescription();
		data.setDescription("description");
		data.setName("name");
		data.setPicture("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		byte[] serialized = GlobalData.serializeDescription(data);
		System.out.write(serialized);
		
		byte[] input = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<description xmlns=\"http://jeffdisher.com/cacophony/description.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <description>description</description>\n"
				+ "    <picture>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</picture>\n"
				+ "</description>\n").getBytes();
		StreamDescription didRead = GlobalData.deserializeDescription(input);
		System.out.println(didRead.getName());
	}

	@Test
	public void testRecommendations() throws IOException {
		StreamRecommendations data = new StreamRecommendations();
		data.getUser().add("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		byte[] serialized = GlobalData.serializeRecommendations(data);
		System.out.write(serialized);
		
		byte[] input = ("<recommendations xmlns=\"http://jeffdisher.com/cacophony/recommendations.xsd\">\n"
				+ "    <user>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</user>\n"
				+ "</recommendations>\n").getBytes();
		StreamRecommendations didRead = GlobalData.deserializeRecommendations(input);
		System.out.println(didRead.getUser().get(0));
	}

	@Test
	public void testPublishWithImage() throws IOException {
		DataElement element = new DataElement();
		element.setCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		element.setMime("image/jpeg");
		element.setSpecial(ElementSpecialType.IMAGE);
		DataArray array = new DataArray();
		array.getElement().add(element);
		StreamRecord record = new StreamRecord();
		record.setName("name");
		record.setElements(array);
		record.setPublisherKey("public key");
		record.setPublishedSecondsUtc((int)(System.currentTimeMillis() / 1000));
		byte[] rawRecord = GlobalData.serializeRecord(record);
		StreamRecord didRead = GlobalData.deserializeRecord(rawRecord);
		Assert.assertEquals("image", ((DataElement)(didRead.getElements().getElement().get(0))).getSpecial().value());
		System.out.println(new String(rawRecord));
	}
}
