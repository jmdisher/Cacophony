package com.jeffdisher.cacophony.data.global;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
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
}
