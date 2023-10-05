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
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;


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

	/**
	 * Serializes a V1 index file to bytes.
	 * 
	 * @param index The object to serialize.
	 * @return The serialized bytes.
	 * @throws SizeConstraintException The serialized data was too big for the protocol.
	 */
	public static byte[] serializeIndex(StreamIndex index) throws SizeConstraintException
	{
		Assert.assertTrue(null != index);
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
		// If we exceed the size, we want to fail out since this means there is something wrong with the data or the
		// spec needs to be updated to a new version, for everyone.
		if (result.length > SizeLimits.MAX_INDEX_SIZE_BYTES)
		{
			throw new SizeConstraintException("StreamIndex", result.length, SizeLimits.MAX_INDEX_SIZE_BYTES);
		}
		return result;
	}

	/**
	 * Deserializes bytes as an index file.
	 * 
	 * @param data The raw bytes.
	 * @return The new instance.
	 * @throws FailedDeserializationException The data was malformed or violated schema.
	 */
	public static StreamIndex deserializeIndex(byte[] data) throws FailedDeserializationException
	{
		Assert.assertTrue(null != data);
		// We should never be called with an invalid size - that should be handled at a higher-level of the system.
		Assert.assertTrue(data.length <= SizeLimits.MAX_INDEX_SIZE_BYTES);
		StreamIndex result = null;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(StreamIndex.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			un.setSchema(INDEX_SCHEMA);
			result = (StreamIndex) un.unmarshal(new ByteArrayInputStream(data));
			
			// If we see an unexpected version, we want to throw an exception since we shouldn't proceed to look at this.
			if (1 != result.getVersion())
			{
				throw new FailedDeserializationException(StreamIndex.class);
			}
		} catch (JAXBException e) {
			throw new FailedDeserializationException(StreamIndex.class);
		}
		return result;
	}

	/**
	 * Serializes a V1 records file to bytes.
	 * 
	 * @param records The object to serialize.
	 * @return The serialized bytes.
	 * @throws SizeConstraintException The serialized data was too big for the protocol.
	 */
	public static byte[] serializeRecords(StreamRecords records) throws SizeConstraintException
	{
		Assert.assertTrue(null != records);
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
		// If we exceed the size, we want to fail out since this means there is something wrong with the data or the
		// spec needs to be updated to a new version, for everyone.
		if (result.length > SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES)
		{
			throw new SizeConstraintException("StreamRecords", result.length, SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES);
		}
		return result;
	}

	/**
	 * Deserializes bytes as a records file.
	 * 
	 * @param data The raw bytes.
	 * @return The new instance.
	 * @throws FailedDeserializationException The data was malformed or violated schema.
	 */
	public static StreamRecords deserializeRecords(byte[] data) throws FailedDeserializationException
	{
		Assert.assertTrue(null != data);
		// We should never be called with an invalid size - that should be handled at a higher-level of the system.
		Assert.assertTrue(data.length <= SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES);
		StreamRecords result = null;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(StreamRecords.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			un.setSchema(RECORDS_SCHEMA);
			result = (StreamRecords) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
			throw new FailedDeserializationException(StreamRecords.class);
		}
		return result;
	}

	/**
	 * Serializes a V1 record file to bytes.
	 * 
	 * @param record The object to serialize.
	 * @return The serialized bytes.
	 * @throws SizeConstraintException The serialized data was too big for the protocol.
	 */
	public static byte[] serializeRecord(StreamRecord record) throws SizeConstraintException
	{
		Assert.assertTrue(null != record);
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
		// If we exceed the size, we want to fail out since this means there is something wrong with the data or the
		// spec needs to be updated to a new version, for everyone.
		if (result.length > SizeLimits.MAX_RECORD_SIZE_BYTES)
		{
			throw new SizeConstraintException("StreamRecord", result.length, SizeLimits.MAX_RECORD_SIZE_BYTES);
		}
		return result;
	}

	/**
	 * Deserializes bytes as a record file.
	 * 
	 * @param data The raw bytes.
	 * @return The new instance.
	 * @throws FailedDeserializationException The data was malformed or violated schema.
	 */
	public static StreamRecord deserializeRecord(byte[] data) throws FailedDeserializationException
	{
		Assert.assertTrue(null != data);
		// We should never be called with an invalid size - that should be handled at a higher-level of the system.
		Assert.assertTrue(data.length <= SizeLimits.MAX_RECORD_SIZE_BYTES);
		StreamRecord result = null;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(StreamRecord.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			un.setSchema(RECORD_SCHEMA);
			result = (StreamRecord) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
			throw new FailedDeserializationException(StreamRecord.class);
		}
		return result;
	}

	/**
	 * Serializes a V1 description file to bytes.
	 * 
	 * @param record The object to serialize.
	 * @return The serialized bytes.
	 * @throws SizeConstraintException The serialized data was too big for the protocol.
	 */
	public static byte[] serializeDescription(StreamDescription record) throws SizeConstraintException
	{
		Assert.assertTrue(null != record);
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
		// If we exceed the size, we want to fail out since this means there is something wrong with the data or the
		// spec needs to be updated to a new version, for everyone.
		if (result.length > SizeLimits.MAX_DESCRIPTION_SIZE_BYTES)
		{
			throw new SizeConstraintException("StreamDescription", result.length, SizeLimits.MAX_DESCRIPTION_SIZE_BYTES);
		}
		return result;
	}

	/**
	 * Deserializes bytes as a description file.
	 * 
	 * @param data The raw bytes.
	 * @return The new instance.
	 * @throws FailedDeserializationException The data was malformed or violated schema.
	 */
	public static StreamDescription deserializeDescription(byte[] data) throws FailedDeserializationException
	{
		Assert.assertTrue(null != data);
		// We should never be called with an invalid size - that should be handled at a higher-level of the system.
		Assert.assertTrue(data.length <= SizeLimits.MAX_DESCRIPTION_SIZE_BYTES);
		StreamDescription result = null;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(StreamDescription.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			un.setSchema(DESCRIPTION_SCHEMA);
			result = (StreamDescription) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
			throw new FailedDeserializationException(StreamDescription.class);
		}
		return result;
	}

	/**
	 * Serializes a V1 recommendations file to bytes.
	 * 
	 * @param record The object to serialize.
	 * @return The serialized bytes.
	 * @throws SizeConstraintException The serialized data was too big for the protocol.
	 */
	public static byte[] serializeRecommendations(StreamRecommendations record) throws SizeConstraintException
	{
		Assert.assertTrue(null != record);
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
		// If we exceed the size, we want to fail out since this means there is something wrong with the data or the
		// spec needs to be updated to a new version, for everyone.
		if (result.length > SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES)
		{
			throw new SizeConstraintException("StreamRecommendations", result.length, SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES);
		}
		return result;
	}

	/**
	 * Deserializes bytes as a recommendations file.
	 * 
	 * @param data The raw bytes.
	 * @return The new instance.
	 * @throws FailedDeserializationException The data was malformed or violated schema.
	 */
	public static StreamRecommendations deserializeRecommendations(byte[] data) throws FailedDeserializationException
	{
		Assert.assertTrue(null != data);
		// We should never be called with an invalid size - that should be handled at a higher-level of the system.
		Assert.assertTrue(data.length <= SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES);
		StreamRecommendations result = null;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(StreamRecommendations.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			un.setSchema(RECOMMENDATIONS_SCHEMA);
			result = (StreamRecommendations) un.unmarshal(new ByteArrayInputStream(data));
		} catch (JAXBException e) {
			throw new FailedDeserializationException(StreamRecommendations.class);
		}
		return result;
	}
}
