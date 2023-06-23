package com.jeffdisher.cacophony.data.global;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.jeffdisher.cacophony.data.global.v2.description.CacophonyDescription;
import com.jeffdisher.cacophony.data.global.v2.extensions.CacophonyExtensionVideo;
import com.jeffdisher.cacophony.data.global.v2.recommendations.CacophonyRecommendations;
import com.jeffdisher.cacophony.data.global.v2.record.CacophonyRecord;
import com.jeffdisher.cacophony.data.global.v2.record.ExtensionReference;
import com.jeffdisher.cacophony.data.global.v2.records.CacophonyRecords;
import com.jeffdisher.cacophony.data.global.v2.root.CacophonyRoot;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits2;


/**
 * A basic abstraction over the management of the version 2 global data model (handles serialization/deserialization).
 */
public class GlobalData2 {
	public static final String ROOT_DATA_TYPE_DESCRIPTION = "cacophony.description";
	public static final String ROOT_DATA_TYPE_RECOMMENDATIONS = "cacophony.recommendations";
	public static final String ROOT_DATA_TYPE_RECORDS = "cacophony.records";

	public static final String DESCRIPTION_MISC_TYPE_EMAIL = "cacophony.email";
	public static final String DESCRIPTION_MISC_TYPE_WEBSITE = "cacophony.website";

	public static final String RECORD_EXTENSION_TYPE_VIDEO = "cacophony.video";

	private static final SchemaFactory SCHEMA_FACTORY = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
	private static final Schema ROOT_SCHEMA;
	private static final Schema DESCRIPTION_SCHEMA;
	private static final Schema RECOMMENDATIONS_SCHEMA;
	private static final Schema RECORDS_SCHEMA;
	private static final Schema RECORD_SCHEMA;
	private static final Schema EXTENSIONS_VIDEO_SCHEMA;

	static {
		try
		{
			ROOT_SCHEMA = SCHEMA_FACTORY.newSchema(GlobalData2.class.getClassLoader().getResource("xsd/root2.xsd"));
			DESCRIPTION_SCHEMA = SCHEMA_FACTORY.newSchema(GlobalData2.class.getClassLoader().getResource("xsd/description2.xsd"));
			RECOMMENDATIONS_SCHEMA = SCHEMA_FACTORY.newSchema(GlobalData2.class.getClassLoader().getResource("xsd/recommendations2.xsd"));
			RECORDS_SCHEMA = SCHEMA_FACTORY.newSchema(GlobalData2.class.getClassLoader().getResource("xsd/records2.xsd"));
			RECORD_SCHEMA = SCHEMA_FACTORY.newSchema(GlobalData2.class.getClassLoader().getResource("xsd/record2.xsd"));
			EXTENSIONS_VIDEO_SCHEMA = SCHEMA_FACTORY.newSchema(GlobalData2.class.getClassLoader().getResource("xsd/extensions2_video.xsd"));
		}
		catch (SAXException e)
		{
			throw new RuntimeException("Fatal error in GlobalData startup", e);
		}
	}

	public static byte[] serializeRoot(CacophonyRoot root) throws SizeConstraintException
	{
		Assert.assertTrue(null != root);
		return _serializeData(new com.jeffdisher.cacophony.data.global.v2.root.ObjectFactory().createRoot(root)
				, SizeLimits2.MAX_ROOT_SIZE_BYTES
				, ROOT_SCHEMA
				, CacophonyRoot.class
		);
	}

	public static CacophonyRoot deserializeRoot(byte[] data) throws FailedDeserializationException
	{
		return _deserializeData(data, SizeLimits2.MAX_ROOT_SIZE_BYTES, ROOT_SCHEMA, CacophonyRoot.class);
	}

	public static byte[] serializeRecords(CacophonyRecords records) throws SizeConstraintException
	{
		Assert.assertTrue(null != records);
		return _serializeData(new com.jeffdisher.cacophony.data.global.v2.records.ObjectFactory().createRecords(records)
				, SizeLimits2.MAX_RECORDS_SIZE_BYTES
				, RECORDS_SCHEMA
				, CacophonyRecords.class
		);
	}

	public static CacophonyRecords deserializeRecords(byte[] data) throws FailedDeserializationException
	{
		return _deserializeData(data, SizeLimits2.MAX_RECORDS_SIZE_BYTES, RECORDS_SCHEMA, CacophonyRecords.class);
	}

	public static byte[] serializeRecord(CacophonyExtendedRecord record) throws SizeConstraintException
	{
		Assert.assertTrue(null != record);
		byte[] result = null;
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			JAXBContext jaxb = JAXBContext.newInstance(CacophonyRecord.class);
			Marshaller m = jaxb.createMarshaller();
			m.setSchema(RECORD_SCHEMA);
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			CacophonyRecord toSave = record.record();
			if (null != record.video())
			{
				ExtensionReference ref = _serializeVideoExtension(record.video());
				toSave.setExtension(ref);
			}
			m.marshal(new com.jeffdisher.cacophony.data.global.v2.record.ObjectFactory().createRecord(toSave) , output);
			result = output.toByteArray();
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		// If we exceed the size, we want to fail out since this means there is something wrong with the data or the
		// spec needs to be updated to a new version, for everyone.
		if (result.length > SizeLimits2.MAX_RECORD_SIZE_BYTES)
		{
			throw new SizeConstraintException("CacophonyRecord", result.length, SizeLimits2.MAX_RECORD_SIZE_BYTES);
		}
		return result;
	}

	public static CacophonyExtendedRecord deserializeRecord(byte[] data) throws FailedDeserializationException
	{
		Assert.assertTrue(null != data);
		// We should never be called with an invalid size - that should be handled at a higher-level of the system.
		Assert.assertTrue(data.length <= SizeLimits2.MAX_RECORD_SIZE_BYTES);
		CacophonyExtendedRecord result = null;
		try {
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
			// https://stackoverflow.com/questions/16038604/unmarshall-xml-using-document-instead-of-streamsource
			builderFactory.setNamespaceAware(true);
			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document xmlDocument = builder.parse(new ByteArrayInputStream(data));
			Node rootElement = xmlDocument.getDocumentElement();
			
			JAXBContext jaxb = JAXBContext.newInstance(CacophonyRecord.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			un.setSchema(RECORD_SCHEMA);
			JAXBElement<CacophonyRecord> root = un.unmarshal(rootElement, CacophonyRecord.class);
			CacophonyRecord record = root.getValue();
			CacophonyExtensionVideo extensionVideo = null;
			
			// If there is an extension, see if it is a type we use.
			if ((null != record.getExtension() && RECORD_EXTENSION_TYPE_VIDEO.equals(record.getExtension().getType())))
			{
				extensionVideo = _deserializeVideoExtension(record.getExtension());
				record.setExtension(null);
			}
			else
			{
				// There is either no extension or we are ignoring it since we don't understand it.
			}
			result = new CacophonyExtendedRecord(record, extensionVideo);
		} catch (JAXBException | ParserConfigurationException | SAXException | IOException e) {
			throw new FailedDeserializationException(CacophonyRecord.class);
		}
		return result;
	}

	public static byte[] serializeDescription(CacophonyDescription description) throws SizeConstraintException
	{
		Assert.assertTrue(null != description);
		return _serializeData(new com.jeffdisher.cacophony.data.global.v2.description.ObjectFactory().createDescription(description)
				, SizeLimits2.MAX_DESCRIPTION_SIZE_BYTES
				, DESCRIPTION_SCHEMA
				, CacophonyDescription.class
		);
	}

	public static CacophonyDescription deserializeDescription(byte[] data) throws FailedDeserializationException
	{
		return _deserializeData(data, SizeLimits2.MAX_DESCRIPTION_SIZE_BYTES, DESCRIPTION_SCHEMA, CacophonyDescription.class);
	}

	public static byte[] serializeRecommendations(CacophonyRecommendations recommendations) throws SizeConstraintException
	{
		Assert.assertTrue(null != recommendations);
		return _serializeData(new com.jeffdisher.cacophony.data.global.v2.recommendations.ObjectFactory().createRecommendations(recommendations)
				, SizeLimits2.MAX_RECOMMENDATIONS_SIZE_BYTES
				, RECOMMENDATIONS_SCHEMA
				, CacophonyRecommendations.class
		);
	}

	public static CacophonyRecommendations deserializeRecommendations(byte[] data) throws FailedDeserializationException
	{
		return _deserializeData(data, SizeLimits2.MAX_RECOMMENDATIONS_SIZE_BYTES, RECOMMENDATIONS_SCHEMA, CacophonyRecommendations.class);
	}


	private static ExtensionReference _serializeVideoExtension(CacophonyExtensionVideo video) throws SizeConstraintException
	{
		Assert.assertTrue(null != video);
		try {
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
			// https://stackoverflow.com/questions/16038604/unmarshall-xml-using-document-instead-of-streamsource
			builderFactory.setNamespaceAware(true);
			DocumentBuilder builder;
			try
			{
				builder = builderFactory.newDocumentBuilder();
			}
			catch (ParserConfigurationException e)
			{
				throw Assert.unexpected(e);
			}
			
			JAXBContext jaxb = JAXBContext.newInstance(CacophonyExtensionVideo.class);
			Marshaller m = jaxb.createMarshaller();
			m.setSchema(EXTENSIONS_VIDEO_SCHEMA);
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			Node container = builder.newDocument().createElement("temp");
			m.marshal(new com.jeffdisher.cacophony.data.global.v2.extensions.ObjectFactory().createVideo(video), container);
			Assert.assertTrue(1 == container.getChildNodes().getLength());
			
			ExtensionReference ref = new com.jeffdisher.cacophony.data.global.v2.record.ObjectFactory().createExtensionReference();
			ref.setType(RECORD_EXTENSION_TYPE_VIDEO);
			ref.getContent().add(container.getFirstChild());
			return ref;
		} catch (JAXBException e) {
			throw new RuntimeException(e);
		}
	}

	private static CacophonyExtensionVideo _deserializeVideoExtension(ExtensionReference extension) throws FailedDeserializationException
	{
		try
		{
			// We are  only expecting the one element at the top-level in the extension (since we are treating it much like a nested document).
			// Note that there could be text complicating that.
			List<Object> list = extension.getContent();
			Node one = null;
			for (Object elt : list)
			{
				if (elt instanceof Node)
				{
					if (null != one)
					{
						// Multiple tags.
						throw new FailedDeserializationException(CacophonyExtensionVideo.class);
					}
					one = (Node)elt;
				}
			}
			if (null == one)
			{
				// Missing tag.
				throw new FailedDeserializationException(CacophonyExtensionVideo.class);
			}
			
			// This is declared as a type we understand so we will fail if we can't interpret it.
			JAXBContext jaxb = JAXBContext.newInstance(CacophonyExtensionVideo.class);
			Unmarshaller un = jaxb.createUnmarshaller();
			un.setSchema(EXTENSIONS_VIDEO_SCHEMA);
			JAXBElement<CacophonyExtensionVideo> extensionRoot = un.unmarshal(one, CacophonyExtensionVideo.class);
			return extensionRoot.getValue();
		}
		catch (JAXBException e)
		{
			throw new FailedDeserializationException(CacophonyExtensionVideo.class);
		}
	}

	private static <T> byte[] _serializeData(JAXBElement<T> element, long maxSizeBytes, Schema schema, Class<T> clazz) throws SizeConstraintException
	{
		byte[] result = null;
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			JAXBContext jaxb = JAXBContext.newInstance(clazz);
			Marshaller m = jaxb.createMarshaller();
			m.setSchema(schema);
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			m.marshal(element, output);
			result = output.toByteArray();
		} catch (JAXBException e) {
			// We aren't expecting this since we started with well-formed data.
			throw Assert.unexpected(e);
		}
		// If we exceed the size, we want to fail out since this means there is something wrong with the data or the
		// spec needs to be updated to a new version, for everyone.
		if (result.length > maxSizeBytes)
		{
			throw new SizeConstraintException(clazz.getName(), result.length, maxSizeBytes);
		}
		return result;
	}

	private static <T> T _deserializeData(byte[] data, long maxSizeBytes, Schema schema, Class<T> clazz) throws FailedDeserializationException
	{
		Assert.assertTrue(null != data);
		// We should never be called with an invalid size - that should be handled at a higher-level of the system.
		Assert.assertTrue(data.length <= maxSizeBytes);
		T result = null;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(clazz);
			Unmarshaller un = jaxb.createUnmarshaller();
			un.setSchema(schema);
			result = un.unmarshal(new StreamSource(new ByteArrayInputStream(data)), clazz).getValue();
		} catch (JAXBException e) {
			throw new FailedDeserializationException(clazz);
		}
		return result;
	}
}
