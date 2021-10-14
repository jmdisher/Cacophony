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
import com.jeffdisher.cacophony.data.global.records.DataArray;
import com.jeffdisher.cacophony.data.global.records.DataElement;
import com.jeffdisher.cacophony.data.global.records.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;


// XML Generation:  https://edwin.baculsoft.com/2019/11/java-generate-xml-from-xsd-using-xjc/
public class Cacophony {

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
		_testData();
		_testDescription();
		_testRecommendations();
	}

	private static void _testIndex() throws JAXBException, PropertyException, SAXException {
		StreamIndex index = new StreamIndex();
		index.setDescription("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setRecommendations("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setStream("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		index.setVersion(5);
		JAXBContext jaxb = JAXBContext.newInstance(StreamIndex.class);
		Marshaller m = jaxb.createMarshaller();
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		m.marshal(index, System.out);
		
		Unmarshaller un = jaxb.createUnmarshaller();
		SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema employeeSchema = sf.newSchema(new File("xsd/global/index.xsd"));
		un.setSchema(employeeSchema);
		StreamIndex didRead = (StreamIndex) un.unmarshal(new ByteArrayInputStream("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><index xmlns=\"http://jeffdisher.com/cacophony/index.xsd\"><version>5</version><description>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</description><recommendations>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</recommendations><stream>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</stream></index>".getBytes()));
		System.out.println(didRead.getStream());
	}

	private static void _testData() throws JAXBException, PropertyException, SAXException {
		StreamRecords data = new StreamRecords();
		StreamRecord record = new StreamRecord();
		data.getRecord().add(record);
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
		JAXBContext jaxb = JAXBContext.newInstance(StreamRecords.class);
		Marshaller m = jaxb.createMarshaller();
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		m.marshal(data, System.out);
		
		Unmarshaller un = jaxb.createUnmarshaller();
		SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema employeeSchema = sf.newSchema(new File("xsd/global/records.xsd"));
		un.setSchema(employeeSchema);
		StreamRecords didRead = (StreamRecords) un.unmarshal(new ByteArrayInputStream(
				("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<records xmlns=\"http://jeffdisher.com/cacophony/records.xsd\">\n"
				+ "    <record>\n"
				+ "        <id>1</id>\n"
				+ "        <name>name</name>\n"
				+ "        <publishedSecondsUtc>555</publishedSecondsUtc>\n"
				+ "        <discussion>URL</discussion>\n"
				+ "        <elements>\n"
				+ "            <element>\n"
				+ "                <cid>QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG</cid>\n"
				+ "                <mime>mim</mime>\n"
				+ "            </element>\n"
				+ "        </elements>\n"
				+ "    </record>\n"
				+ "</records>\n").getBytes())
		);
		System.out.println(didRead.getRecord().get(0).getName());
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
