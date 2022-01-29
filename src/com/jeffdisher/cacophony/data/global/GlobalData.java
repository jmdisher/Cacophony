package com.jeffdisher.cacophony.data.global;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

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
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A basic abstraction over the management of the global data model (handles serialization/deserialization).
 */
public class GlobalData {
	private static final SchemaFactory SCHEMA_FACTORY = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
	private static final Schema INDEX_SCHEMA;
	private static final Schema RECORDS_SCHEMA;
	private static final Schema RECORD_SCHEMA;
	private static final Schema DESCRIPTION_SCHEMA;
	private static final Schema RECOMMENDATIONS_SCHEMA;

	static {
		try
		{
			INDEX_SCHEMA = SCHEMA_FACTORY.newSchema(GlobalData.class.getClassLoader().getResource("xsd/global/index.xsd"));
			RECORDS_SCHEMA = SCHEMA_FACTORY.newSchema(GlobalData.class.getClassLoader().getResource("xsd/global/records.xsd"));
			RECORD_SCHEMA = SCHEMA_FACTORY.newSchema(GlobalData.class.getClassLoader().getResource("xsd/global/record.xsd"));
			DESCRIPTION_SCHEMA = SCHEMA_FACTORY.newSchema(GlobalData.class.getClassLoader().getResource("xsd/global/description.xsd"));
			RECOMMENDATIONS_SCHEMA = SCHEMA_FACTORY.newSchema(GlobalData.class.getClassLoader().getResource("xsd/global/recommendations.xsd"));
		}
		catch (SAXException e)
		{
			throw new RuntimeException("Fatal error in GlobalData startup", e);
		}
	}

	public static byte[] serializeIndex(StreamIndex index)
	{
		byte[] result = null;
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			JAXBContext jaxb = JAXBContext.newInstance(StreamIndex.class);
			Marshaller m = jaxb.createMarshaller();
			m.setSchema(INDEX_SCHEMA);
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
		Assert.assertTrue(null != data);
		StreamIndex result = null;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(StreamIndex.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			un.setSchema(INDEX_SCHEMA);
			result = (StreamIndex) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
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
			m.setSchema(RECORDS_SCHEMA);
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
			un.setSchema(RECORDS_SCHEMA);
			result = (StreamRecords) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
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
			m.setSchema(RECORD_SCHEMA);
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
			un.setSchema(RECORD_SCHEMA);
			result = (StreamRecord) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
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
			m.setSchema(DESCRIPTION_SCHEMA);
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
			un.setSchema(DESCRIPTION_SCHEMA);
			result = (StreamDescription) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
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
			m.setSchema(RECOMMENDATIONS_SCHEMA);
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
			un.setSchema(RECOMMENDATIONS_SCHEMA);
			result = (StreamRecommendations) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return result;
	}
}
