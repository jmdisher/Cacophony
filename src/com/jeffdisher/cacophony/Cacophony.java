package com.jeffdisher.cacophony;

import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

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

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;


// XML Generation:  https://edwin.baculsoft.com/2019/11/java-generate-xml-from-xsd-using-xjc/
public class Cacophony {
	/**
	 * Argument modes:
	 * "--createNewChannel" Used to create a new empty channel for this local key.
	 * "--destroyThisChannel" Used to destroy any existing local channel by unbinding the publish and unpinning all channel entries.
	 * "--updateDescription" Changes the description of the local channel.
	 * "--readDescription" Reads the description of the local channel, writing it to stdout.
	 * "--addRecommendation" Adds a new channel key to the recommended list from the local channel.
	 * "--removeRecommendation" Removes a channel key from the recommended list from the local channel.
	 * "--listRecommendations" Lists the recommended channel keys from the local channel to stdout.
	 * "--addToThisChannel" Adds and publishes a new entry to the local channel.
	 * "--listThisChannel" Lists all the entries published to this channel to stdout.
	 * "--removeFromThisChannel" Removes a given entry from the local channel.
	 * 
	 * "--setPreferredVideoSize" Sets the maximum dimension size to use when locally caching.
	 * "--setCacheLimitForRemoteChannel" Sets the cache limit for the given channel.
	 * "--updateNextFollowing" Does the polling cycle on the next channel being followed and advances polling state to the next.
	 * "--startFollowing" Adds the given channel ID to the following set.
	 * "--stopFollowing" Removes the given channel ID from the following set.
	 * "--readRemoteDescription" Reads the description of a channel ID and prints it to stdout.
	 * "--readRemoteRecommendations" Reads the recommended channel IDs of the given ID and prints them to stdout.
	 * "--readRemoteChannel" Reads the contents of a given channel ID and prints it to stdout.
	 * "--favouriteElement" Adds the given element to the favourites pool (removing it from other pools if already present).
	 * "--unfavouriteElement" Removes the given element from the favourites pool.
	 * "--explicitLoadElement" Adds the given element to the explicit pool (ignoring this already present in another pool).
	 * "--readToLocalFile" Reads the default data from the given element into a file on the local filesystem (implicitly adding the element to the explicit pool)
	 * 
	 * @param args
	 * @throws IOException
	 * @throws JAXBException
	 * @throws SAXException
	 */
	public static void main(String[] args) throws IOException, JAXBException, SAXException {
		/*
		IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
		Map<?, ?> map = ipfs.commands();
		for (Map.Entry<?, ?> elt : map.entrySet()) {
			System.out.println("KEY \"" + elt.getKey() + "\" -> VALUE \"" + elt.getValue() + "\"");
		}
		*/
		/*
		byte[] bytes = "Testing data".getBytes();
		NamedStreamable.ByteArrayWrapper wrapper = new NamedStreamable.ByteArrayWrapper(bytes);
		MerkleNode result = ipfs.add(wrapper).get(0);
		
		byte[] hash = result.hash.toBytes();
		String hexString = String.format("%0" + (hash.length * 2) + "x", new BigInteger(1, hash));
		System.out.println("Uploaded: " + result.toString() + " - " + hexString);
		*/
		/*
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		XMLEncoder encoder = new XMLEncoder(stream);
		encoder.writeObject("Testing");
		encoder.writeObject(555L);
		encoder.close();
		*/
		_testIndex();
		_testRecords();
		_testRecord();
		_testDescription();
		_testRecommendations();
	}

	private static void _testIndex() throws JAXBException, PropertyException, SAXException {
		StreamIndex index = new StreamIndex();
		index.setDescription("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setRecommendations("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setRecords("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setVersion(5);
		JAXBContext jaxb = JAXBContext.newInstance(StreamIndex.class);
		Marshaller m = jaxb.createMarshaller();
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		m.marshal(index, System.out);
		
		Unmarshaller un = jaxb.createUnmarshaller();
		SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema employeeSchema = sf.newSchema(new File("xsd/global/index.xsd"));
		un.setSchema(employeeSchema);
		StreamIndex didRead = (StreamIndex) un.unmarshal(new ByteArrayInputStream("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><index xmlns=\"http://jeffdisher.com/cacophony/index.xsd\"><version>5</version><description>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</description><recommendations>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</recommendations><records>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</records></index>".getBytes()));
		System.out.println(didRead.getRecords());
	}

	private static void _testRecords() throws JAXBException, PropertyException, SAXException {
		StreamRecords data = new StreamRecords();
		data.getRecord().add("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		JAXBContext jaxb = JAXBContext.newInstance(StreamRecords.class);
		Marshaller m = jaxb.createMarshaller();
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		m.marshal(data, System.out);
		
		Unmarshaller un = jaxb.createUnmarshaller();
		SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema recordsSchema = sf.newSchema(new File("xsd/global/records.xsd"));
		un.setSchema(recordsSchema);
		StreamRecords readRecords = (StreamRecords) un.unmarshal(new ByteArrayInputStream(
				("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<records xmlns=\"http://jeffdisher.com/cacophony/records.xsd\">\n"
				+ "    <record>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</record>\n"
				+ "</records>\n").getBytes())
		);
		System.out.println(readRecords.getRecord().get(0));
	}

	private static void _testRecord() throws JAXBException, PropertyException, SAXException {
		StreamRecord record = new StreamRecord();
		record.setDiscussion("URL");
		record.setId(1);
		record.setName("name");
		record.setPublishedSecondsUtc(555);
		DataArray eltArray = new DataArray();
		record.setElements(eltArray);
		DataElement element = new DataElement();
		eltArray.getElement().add(element);
		element.setCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		element.setMime("mim");
		JAXBContext jaxb = JAXBContext.newInstance(StreamRecord.class);
		Marshaller m = jaxb.createMarshaller();
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		m.marshal(record, System.out);
		
		Unmarshaller un = jaxb.createUnmarshaller();
		SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema recordSchema = sf.newSchema(new File("xsd/global/record.xsd"));
		un.setSchema(recordSchema);
		StreamRecord readRecord = (StreamRecord) un.unmarshal(new ByteArrayInputStream(
				("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<record xmlns=\"http://jeffdisher.com/cacophony/record.xsd\">\n"
				+ "    <id>1</id>\n"
				+ "    <name>name</name>\n"
				+ "    <publishedSecondsUtc>555</publishedSecondsUtc>\n"
				+ "    <discussion>URL</discussion>\n"
				+ "    <elements>\n"
				+ "        <element>\n"
				+ "            <cid>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</cid>\n"
				+ "            <mime>mim</mime>\n"
				+ "        </element>\n"
				+ "    </elements>\n"
				+ "</record>\n").getBytes())
		);
		System.out.println(readRecord.getName());
	}

	private static void _testDescription() throws JAXBException, PropertyException, SAXException {
		StreamDescription data = new StreamDescription();
		data.setDescription("description");
		data.setName("name");
		data.setPicture("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		JAXBContext jaxb = JAXBContext.newInstance(StreamDescription.class);
		Marshaller m = jaxb.createMarshaller();
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		m.marshal(data, System.out);
		
		Unmarshaller un = jaxb.createUnmarshaller();
		SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema employeeSchema = sf.newSchema(new File("xsd/global/description.xsd"));
		un.setSchema(employeeSchema);
		StreamDescription didRead = (StreamDescription) un.unmarshal(new ByteArrayInputStream(
				("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<description xmlns=\"http://jeffdisher.com/cacophony/description.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <description>description</description>\n"
				+ "    <picture>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</picture>\n"
				+ "</description>\n").getBytes())
		);
		System.out.println(didRead.getName());
	}

	private static void _testRecommendations() throws JAXBException, PropertyException, SAXException {
		StreamRecommendations data = new StreamRecommendations();
		data.getUser().add("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		JAXBContext jaxb = JAXBContext.newInstance(StreamRecommendations.class);
		Marshaller m = jaxb.createMarshaller();
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		m.marshal(data, System.out);
		
		Unmarshaller un = jaxb.createUnmarshaller();
		SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema employeeSchema = sf.newSchema(new File("xsd/global/recommendations.xsd"));
		un.setSchema(employeeSchema);
		StreamRecommendations didRead = (StreamRecommendations) un.unmarshal(new ByteArrayInputStream(
				("<recommendations xmlns=\"http://jeffdisher.com/cacophony/recommendations.xsd\">\n"
				+ "    <user>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</user>\n"
				+ "</recommendations>\n").getBytes())
		);
		System.out.println(didRead.getUser().get(0));
	}
}
