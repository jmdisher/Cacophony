package com.jeffdisher.cacophony.data.global;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;


/**
 * A basic abstraction over the management of the global data model (handles serialization/deserialization).
 */
public class GlobalData {
	public static byte[] serializeIndex(StreamIndex index)
	{
		byte[] result = null;
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			JAXBContext jaxb = JAXBContext.newInstance(StreamIndex.class);
			Marshaller m = jaxb.createMarshaller();
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			m.marshal(index, output);
			result = output.toByteArray();
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return result;
	}

	public static StreamIndex deserializeIndex(byte[] data)
	{
		StreamIndex result = null;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(StreamIndex.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema employeeSchema = sf.newSchema(new File("xsd/global/index.xsd"));
			un.setSchema(employeeSchema);
			result = (StreamIndex) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (SAXException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return result;
	}

	public static byte[] serializeRecords(StreamRecords records)
	{
		byte[] result = null;
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			JAXBContext jaxb = JAXBContext.newInstance(StreamRecords.class);
			Marshaller m = jaxb.createMarshaller();
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			m.marshal(records, output);
			result = output.toByteArray();
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return result;
	}

	public static StreamRecords deserializeRecords(byte[] data)
	{
		StreamRecords result = null;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(StreamRecords.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema employeeSchema = sf.newSchema(new File("xsd/global/records.xsd"));
			un.setSchema(employeeSchema);
			result = (StreamRecords) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (SAXException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return result;
	}

	public static byte[] serializeRecord(StreamRecord record)
	{
		byte[] result = null;
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			JAXBContext jaxb = JAXBContext.newInstance(StreamRecord.class);
			Marshaller m = jaxb.createMarshaller();
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			m.marshal(record, output);
			result = output.toByteArray();
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return result;
	}

	public static StreamRecord deserializeRecord(byte[] data)
	{
		StreamRecord result = null;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(StreamRecord.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema employeeSchema = sf.newSchema(new File("xsd/global/record.xsd"));
			un.setSchema(employeeSchema);
			result = (StreamRecord) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (SAXException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return result;
	}

	public static byte[] serializeDescription(StreamDescription record)
	{
		byte[] result = null;
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			JAXBContext jaxb = JAXBContext.newInstance(StreamDescription.class);
			Marshaller m = jaxb.createMarshaller();
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			m.marshal(record, output);
			result = output.toByteArray();
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return result;
	}

	public static StreamDescription deserializeDescription(byte[] data)
	{
		StreamDescription result = null;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(StreamDescription.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema employeeSchema = sf.newSchema(new File("xsd/global/description.xsd"));
			un.setSchema(employeeSchema);
			result = (StreamDescription) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (SAXException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return result;
	}

	public static byte[] serializeRecommendations(StreamRecommendations record)
	{
		byte[] result = null;
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			JAXBContext jaxb = JAXBContext.newInstance(StreamRecommendations.class);
			Marshaller m = jaxb.createMarshaller();
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			m.marshal(record, output);
			result = output.toByteArray();
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return result;
	}

	public static StreamRecommendations deserializeRecommendations(byte[] data)
	{
		StreamRecommendations result = null;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(StreamRecommendations.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema employeeSchema = sf.newSchema(new File("xsd/global/recommendations.xsd"));
			un.setSchema(employeeSchema);
			result = (StreamRecommendations) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (SAXException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return result;
	}


	public static void main(String[] args) throws IOException, JAXBException, SAXException {
		_testIndex();
		_testRecords();
		_testRecord();
		_testDescription();
		_testRecommendations();
	}

	private static void _testIndex() throws IOException {
		StreamIndex index = new StreamIndex();
		index.setDescription("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setRecommendations("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setRecords("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setVersion(5);
		byte[] serialized = serializeIndex(index);
		System.out.write(serialized);
		byte[] input = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><index xmlns=\"http://jeffdisher.com/cacophony/index.xsd\"><version>5</version><description>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</description><recommendations>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</recommendations><records>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</records></index>".getBytes();
		StreamIndex didRead = deserializeIndex(input);
		System.out.println(didRead.getRecords());
	}

	private static void _testRecords() throws IOException {
		StreamRecords data = new StreamRecords();
		data.getRecord().add("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		byte[] serialized = serializeRecords(data);
		System.out.write(serialized);
		
		byte[] input = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<records xmlns=\"http://jeffdisher.com/cacophony/records.xsd\">\n"
				+ "    <record>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</record>\n"
				+ "</records>\n").getBytes();
		StreamRecords readRecords = deserializeRecords(input);
		System.out.println(readRecords.getRecord().get(0));
	}

	private static void _testRecord() throws IOException {
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
		byte[] serialized = serializeRecord(record);
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
		StreamRecord readRecord = deserializeRecord(input);
		System.out.println(readRecord.getName());
	}

	private static void _testDescription() throws IOException {
		StreamDescription data = new StreamDescription();
		data.setDescription("description");
		data.setName("name");
		data.setPicture("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		byte[] serialized = serializeDescription(data);
		System.out.write(serialized);
		
		byte[] input = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<description xmlns=\"http://jeffdisher.com/cacophony/description.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <description>description</description>\n"
				+ "    <picture>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</picture>\n"
				+ "</description>\n").getBytes();
		StreamDescription didRead = deserializeDescription(input);
		System.out.println(didRead.getName());
	}

	private static void _testRecommendations() throws IOException {
		StreamRecommendations data = new StreamRecommendations();
		data.getUser().add("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		byte[] serialized = serializeRecommendations(data);
		System.out.write(serialized);
		
		byte[] input = ("<recommendations xmlns=\"http://jeffdisher.com/cacophony/recommendations.xsd\">\n"
				+ "    <user>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</user>\n"
				+ "</recommendations>\n").getBytes();
		StreamRecommendations didRead = deserializeRecommendations(input);
		System.out.println(didRead.getUser().get(0));
	}
}
