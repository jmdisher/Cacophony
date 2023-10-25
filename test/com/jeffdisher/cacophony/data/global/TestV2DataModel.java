package com.jeffdisher.cacophony.data.global;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Node;

import com.jeffdisher.cacophony.data.global.v2.description.CacophonyDescription;
import com.jeffdisher.cacophony.data.global.v2.description.MiscData;
import com.jeffdisher.cacophony.data.global.v2.description.PictureReference;
import com.jeffdisher.cacophony.data.global.v2.extensions.CacophonyExtensionVideo;
import com.jeffdisher.cacophony.data.global.v2.extensions.VideoFormat;
import com.jeffdisher.cacophony.data.global.v2.recommendations.CacophonyRecommendations;
import com.jeffdisher.cacophony.data.global.v2.record.CacophonyRecord;
import com.jeffdisher.cacophony.data.global.v2.record.ExtensionReference;
import com.jeffdisher.cacophony.data.global.v2.record.ThumbnailReference;
import com.jeffdisher.cacophony.data.global.v2.records.CacophonyRecords;
import com.jeffdisher.cacophony.data.global.v2.root.CacophonyRoot;
import com.jeffdisher.cacophony.data.global.v2.root.DataReference;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import io.ipfs.cid.Cid;


public class TestV2DataModel
{
	private static final IpfsFile H1 = _generateHash(new byte[] {1});
	private static final IpfsFile H2 = _generateHash(new byte[] {2});

	@Test
	public void readRoot() throws Throwable
	{
		String raw = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<root xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/root2.xsd\">\n"
				+ "    <version>5</version>\n"
				+ "    <data type=\"type1\">" + H1.toSafeString() + "</data>\n"
				+ "    <data type=\"type2\">" + H2.toSafeString() + "</data>\n"
				+ "</root>\n";
		CacophonyRoot root = GlobalData2.deserializeRoot(raw.getBytes());
		Assert.assertEquals(5, root.getVersion());
		List<DataReference> data = root.getData();
		Assert.assertEquals(2, data.size());
		Assert.assertEquals("type1", data.get(0).getType());
		Assert.assertEquals(H1.toSafeString(), data.get(0).getValue());
		Assert.assertEquals("type2", data.get(1).getType());
		Assert.assertEquals(H2.toSafeString(), data.get(1).getValue());
	}

	@Test
	public void writeRoot() throws Throwable
	{
		CacophonyRoot root = new CacophonyRoot();
		root.setVersion(2);
		List<DataReference> refs = root.getData();
		DataReference ref1 = new DataReference();
		ref1.setType("type1");
		ref1.setValue(H1.toSafeString());
		refs.add(ref1);
		DataReference ref2 = new DataReference();
		ref2.setType("type2");
		ref2.setValue(H2.toSafeString());
		refs.add(ref2);
		byte[] data = GlobalData2.serializeRoot(root);
		String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<root xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/root2.xsd\">\n"
				+ "    <version>2</version>\n"
				+ "    <data type=\"type1\">" + H1.toSafeString() + "</data>\n"
				+ "    <data type=\"type2\">" + H2.toSafeString() + "</data>\n"
				+ "</root>\n"
		;
		Assert.assertEquals(expected, new String(data));
	}

	@Test
	public void readRecords() throws Throwable
	{
		String raw = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<records xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/records2.xsd\">\n"
				+ "    <record>" + H1.toSafeString() + "</record>\n"
				+ "    <record>" + H2.toSafeString() + "</record>\n"
				+ "</records>\n"
		;
		CacophonyRecords records = GlobalData2.deserializeRecords(raw.getBytes());
		List<String> list = records.getRecord();
		Assert.assertEquals(2, list.size());
		Assert.assertEquals(H1.toSafeString(), list.get(0));
		Assert.assertEquals(H2.toSafeString(), list.get(1));
	}

	@Test
	public void writeRecords() throws Throwable
	{
		CacophonyRecords records = new CacophonyRecords();
		List<String> list = records.getRecord();
		list.add(H1.toSafeString());
		list.add(H2.toSafeString());
		byte[] data = GlobalData2.serializeRecords(records);
		String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<records xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/records2.xsd\">\n"
				+ "    <record>" + H1.toSafeString() + "</record>\n"
				+ "    <record>" + H2.toSafeString() + "</record>\n"
				+ "</records>\n"
		;
		Assert.assertEquals(expected, new String(data));
	}

	@Test
	public void readRecord() throws Throwable
	{
		String raw = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<record xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <publishedSecondsUtc>5</publishedSecondsUtc>\n"
				+ "    <discussionUrl>http://example.com/</discussionUrl>\n"
				+ "    <publisherKey>" + K0.toPublicKey() + "</publisherKey>\n"
				+ "    <replyTo>" + H1.toSafeString() + "</replyTo>\n"
				+ "</record>\n"
		;
		CacophonyExtendedRecord wrapper = GlobalData2.deserializeRecord(raw.getBytes());
		CacophonyRecord record = wrapper.record();
		Assert.assertEquals("name", record.getName());
		Assert.assertEquals(5L, record.getPublishedSecondsUtc());
		Assert.assertEquals("http://example.com/", record.getDiscussionUrl());
		Assert.assertEquals(K0.toPublicKey(), record.getPublisherKey());
		Assert.assertEquals(H1.toSafeString(), record.getReplyTo());
	}

	@Test
	public void readRecordWithEmbeddedExtension() throws Throwable
	{
		String raw = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<record xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <publishedSecondsUtc>5</publishedSecondsUtc>\n"
				+ "    <discussionUrl>http://example.com/</discussionUrl>\n"
				+ "    <publisherKey>" + K0.toPublicKey() + "</publisherKey>\n"
				
				+ "<extension type=\"cacophony.video\">\n"
				+ "<video xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/extensions2_video.xsd\">\n"
				+ "    <format>\n"
				+ "        <cid>" + H1.toSafeString() + "</cid>\n"
				+ "        <mime>video/ogg</mime>\n"
				+ "        <height>1</height>\n"
				+ "        <width>1</width>\n"
				+ "    </format>\n"
				+ "    <format>\n"
				+ "        <cid>" + H2.toSafeString() + "</cid>\n"
				+ "        <mime>video/ogg</mime>\n"
				+ "        <height>2</height>\n"
				+ "        <width>2</width>\n"
				+ "    </format>\n"
				+ "</video>\n"
				+ "</extension>\n"
				
				+ "    <replyTo>" + H1.toSafeString() + "</replyTo>\n"
				+ "</record>\n"
		;
		CacophonyExtendedRecord wrapper = GlobalData2.deserializeRecord(raw.getBytes());
		CacophonyRecord record = wrapper.record();
		Assert.assertEquals("name", record.getName());
		Assert.assertEquals(5L, record.getPublishedSecondsUtc());
		Assert.assertEquals("http://example.com/", record.getDiscussionUrl());
		Assert.assertEquals(K0.toPublicKey(), record.getPublisherKey());
		Assert.assertEquals(H1.toSafeString(), record.getReplyTo());
		CacophonyExtensionVideo extension = wrapper.video();
		List<VideoFormat> videos = extension.getFormat();
		Assert.assertEquals(2, videos.size());
		Assert.assertEquals(H1.toSafeString(), videos.get(0).getCid());
		Assert.assertEquals(H2.toSafeString(), videos.get(1).getCid());
	}

	@Test
	public void readRecordWithUnknownExtension() throws Throwable
	{
		String raw = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<record xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <publishedSecondsUtc>5</publishedSecondsUtc>\n"
				+ "    <discussionUrl>http://example.com/</discussionUrl>\n"
				+ "    <publisherKey>" + K0.toPublicKey() + "</publisherKey>\n"

				+ "<extension type=\"unknown type\">\n"
				+ "we will pretent that this\n"
				+ "is just a text sequence with\n"
				+ "an embedded <tag>tag as that should</tag> be\n"
				+ "\n"
				+ "interesting to parse\n"
				+ "</extension>\n"
				+ "    <replyTo>" + H1.toSafeString() + "</replyTo>\n"
				+ "</record>\n"
		;
		CacophonyExtendedRecord wrapper = GlobalData2.deserializeRecord(raw.getBytes());
		CacophonyRecord record = wrapper.record();
		Assert.assertEquals("name", record.getName());
		Assert.assertEquals(5L, record.getPublishedSecondsUtc());
		Assert.assertEquals("http://example.com/", record.getDiscussionUrl());
		Assert.assertEquals(K0.toPublicKey(), record.getPublisherKey());
		Assert.assertEquals(H1.toSafeString(), record.getReplyTo());
		
		ExtensionReference ref = record.getExtension();
		Assert.assertEquals("unknown type", ref.getType());
		List<Object> contents = ref.getContent();
		// We expect 3 elements:  String, Node (actually com.sun.org.apache.xerces.internal.dom.ElementNSImpl), String.
		Assert.assertEquals(3, contents.size());
		Assert.assertEquals(String.class, contents.get(0).getClass());
		Assert.assertTrue(contents.get(1) instanceof Node);
		Assert.assertEquals(String.class, contents.get(2).getClass());
	}

	@Test
	public void writeRecord() throws Throwable
	{
		CacophonyRecord record = new CacophonyRecord();
		record.setName("name");
		record.setPublishedSecondsUtc(5L);
		record.setDiscussionUrl("http://example.com/");
		record.setPublisherKey(K0.toPublicKey());
		record.setReplyTo(H1.toSafeString());
		byte[] data = GlobalData2.serializeRecord(new CacophonyExtendedRecord(record, null));
		String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<record xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <publishedSecondsUtc>5</publishedSecondsUtc>\n"
				+ "    <discussionUrl>http://example.com/</discussionUrl>\n"
				+ "    <publisherKey>" + K0.toPublicKey() + "</publisherKey>\n"
				+ "    <replyTo>" + H1.toSafeString() + "</replyTo>\n"
				+ "</record>\n"
		;
		Assert.assertEquals(expected, new String(data));
	}

	@Test
	public void writeRecordWithEmbeddedExtension() throws Throwable
	{
		CacophonyRecord record = new CacophonyRecord();
		record.setName("name");
		record.setPublishedSecondsUtc(5L);
		record.setDiscussionUrl("http://example.com/");
		record.setPublisherKey(K0.toPublicKey());
		record.setReplyTo(H1.toSafeString());
		CacophonyExtensionVideo video = new CacophonyExtensionVideo();
		VideoFormat one = new VideoFormat();
		IpfsFile file1 = _generateHash(new byte[] { 1 });
		one.setCid(file1.toSafeString());
		one.setHeight(1);
		one.setWidth(1);
		one.setMime("video/webm");
		VideoFormat two = new VideoFormat();
		IpfsFile file2 = _generateHash(new byte[] { 2 });
		two.setCid(file2.toSafeString());
		two.setHeight(2);
		two.setWidth(2);
		two.setMime("video/webm");
		video.getFormat().add(one);
		video.getFormat().add(two);
		byte[] data = GlobalData2.serializeRecord(new CacophonyExtendedRecord(record, video));
		String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<record xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <publishedSecondsUtc>5</publishedSecondsUtc>\n"
				+ "    <discussionUrl>http://example.com/</discussionUrl>\n"
				+ "    <publisherKey>" + K0.toPublicKey() + "</publisherKey>\n"
				+ "    <extension type=\"cacophony.video\">\n"
				+ "        <video xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/extensions2_video.xsd\">\n"
				+ "            <format>\n"
				+ "                <cid>" + file1.toSafeString() + "</cid>\n"
				+ "                <mime>video/webm</mime>\n"
				+ "                <height>1</height>\n"
				+ "                <width>1</width>\n"
				+ "            </format>\n"
				+ "            <format>\n"
				+ "                <cid>" + file2.toSafeString() + "</cid>\n"
				+ "                <mime>video/webm</mime>\n"
				+ "                <height>2</height>\n"
				+ "                <width>2</width>\n"
				+ "            </format>\n"
				+ "        </video>\n"
				+ "    </extension>\n"
				+ "    <replyTo>QmNLei8bFQqm4jWuEeZwxamXFeVN2UJTGzDy2hBr1SGih5</replyTo>\n"
				+ "</record>\n"
		;
		Assert.assertEquals(expected, new String(data));
	}

	@Test
	public void readDescription() throws Throwable
	{
		IpfsFile pic = _generateHash(new byte[] { 1 });
		String raw = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<description xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/description2.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <picture mime=\"mime\">" + pic.toSafeString() + "</picture>\n"
				+ "    <misc type=\"cacophony.website\">http://example.com/</misc>\n"
				+ "</description>\n"
		;
		CacophonyDescription description = GlobalData2.deserializeDescription(raw.getBytes());
		Assert.assertEquals("name", description.getName());
		Assert.assertEquals("mime", description.getPicture().getMime());
		Assert.assertEquals(pic.toSafeString(), description.getPicture().getValue());
		List<MiscData> misc = description.getMisc();
		Assert.assertEquals(1, misc.size());
		Assert.assertEquals("cacophony.website", misc.get(0).getType());
		Assert.assertEquals("http://example.com/", misc.get(0).getValue());
	}

	@Test
	public void writeDescription() throws Throwable
	{
		IpfsFile pic = _generateHash(new byte[] { 1 });
		CacophonyDescription description = new CacophonyDescription();
		description.setName("name");
		PictureReference ref = new PictureReference();
		ref.setMime("mime");
		ref.setValue(pic.toSafeString());
		description.setPicture(ref);
		MiscData misc = new MiscData();
		misc.setType("cacophony.website");
		misc.setValue("http://example.com/");
		description.getMisc().add(misc);
		byte[] data = GlobalData2.serializeDescription(description);
		String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<description xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/description2.xsd\">\n"
				+ "    <name>name</name>\n"
				+ "    <picture mime=\"mime\">" + pic.toSafeString() + "</picture>\n"
				+ "    <misc type=\"cacophony.website\">http://example.com/</misc>\n"
				+ "</description>\n"
		;
		Assert.assertEquals(expected, new String(data));
	}

	@Test
	public void readRecommendations() throws Throwable
	{
		String raw = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<recommendations xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/recommendations2.xsd\">\n"
				+ "    <user>" + K0.toPublicKey() + "</user>\n"
				+ "    <user>" + K1.toPublicKey() + "</user>\n"
				+ "</recommendations>\n"
		;
		CacophonyRecommendations recommendations = GlobalData2.deserializeRecommendations(raw.getBytes());
		List<String> list = recommendations.getUser();
		Assert.assertEquals(2, list.size());
		Assert.assertEquals(K0.toPublicKey(), list.get(0));
		Assert.assertEquals(K1.toPublicKey(), list.get(1));
	}

	@Test
	public void writeRecommendations() throws Throwable
	{
		CacophonyRecommendations recommendations = new CacophonyRecommendations();
		List<String> list = recommendations.getUser();
		list.add(K0.toPublicKey());
		list.add(K1.toPublicKey());
		byte[] data = GlobalData2.serializeRecommendations(recommendations);
		String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<recommendations xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/recommendations2.xsd\">\n"
				+ "    <user>" + K0.toPublicKey() + "</user>\n"
				+ "    <user>" + K1.toPublicKey() + "</user>\n"
				+ "</recommendations>\n"
		;
		Assert.assertEquals(expected, new String(data));
	}

	@Test
	public void writeFullTree() throws Throwable
	{
		// This test is long since it is an exhaustive test of the entire use-case, to make sure nothing is missing.
		// First, write all the data.
		String post1Title = "post 1";
		String post1Desc = "This is a\nbasic post";
		long publishedSeconds1 = 1L;
		CacophonyRecord record1 = new CacophonyRecord();
		record1.setName(post1Title);
		record1.setDescription(post1Desc);
		record1.setPublishedSecondsUtc(publishedSeconds1);
		record1.setPublisherKey(K0.toPublicKey());
		byte[] data_record1 = GlobalData2.serializeRecord(new CacophonyExtendedRecord(record1, null));
		IpfsFile record1Cid = _generateHash(data_record1);
		
		IpfsFile video = _generateHash(new byte[] { 3 });
		VideoFormat videoFormat = new VideoFormat();
		videoFormat.setMime("video/webm");
		videoFormat.setHeight(480);
		videoFormat.setWidth(640);
		videoFormat.setCid(video.toSafeString());
		IpfsFile audio = _generateHash(new byte[] { 4 });
		VideoFormat audioFormat = new VideoFormat();
		audioFormat.setMime("audio/ogg");
		audioFormat.setCid(audio.toSafeString());
		CacophonyExtensionVideo extension = new CacophonyExtensionVideo();
		extension.getFormat().add(videoFormat);
		extension.getFormat().add(audioFormat);
		
		String post2Title = "Post with content";
		String post2Desc = "This is a\nmuch\nbigger post";
		long publishedSeconds2 = 2L;
		IpfsFile thumbnail = _generateHash(new byte[] { 2 });
		CacophonyRecord record2 = new CacophonyRecord();
		record2.setName(post2Title);
		record2.setDescription(post2Desc);
		record2.setPublishedSecondsUtc(publishedSeconds2);
		record2.setPublisherKey(K0.toPublicKey());
		ThumbnailReference thumbRef = new ThumbnailReference();
		thumbRef.setMime("image/jpeg");
		thumbRef.setValue(thumbnail.toSafeString());
		record2.setThumbnail(thumbRef);
		byte[] data_record2 = GlobalData2.serializeRecord(new CacophonyExtendedRecord(record2, extension));
		IpfsFile record2Cid = _generateHash(data_record2);
		
		CacophonyRecords records = new CacophonyRecords();
		records.getRecord().add(record1Cid.toSafeString());
		records.getRecord().add(record2Cid.toSafeString());
		byte[] data_records = GlobalData2.serializeRecords(records);
		IpfsFile recordsCid = _generateHash(data_records);
		
		CacophonyRecommendations recommendations = new CacophonyRecommendations();
		recommendations.getUser().add(K1.toPublicKey());
		byte[] data_recommendations = GlobalData2.serializeRecommendations(recommendations);
		IpfsFile recommendationsCid = _generateHash(data_recommendations);
		
		String userName = "user name";
		String userDescription = "This is\nthe user description";
		IpfsFile userPic = _generateHash(new byte[] { 1 });
		CacophonyDescription description = new CacophonyDescription();
		description.setName(userName);
		description.setDescription(userDescription);
		PictureReference picRef = new PictureReference();
		picRef.setMime("image/jpeg");
		picRef.setValue(userPic.toSafeString());
		description.setPicture(picRef);
		byte[] data_description = GlobalData2.serializeDescription(description);
		IpfsFile descriptionCid = _generateHash(data_description);
		
		CacophonyRoot root = new CacophonyRoot();
		root.setVersion(2);
		DataReference dataRecords = new DataReference();
		dataRecords.setType(GlobalData2.ROOT_DATA_TYPE_RECORDS);
		dataRecords.setValue(recordsCid.toSafeString());
		DataReference dataRecommendations = new DataReference();
		dataRecommendations.setType(GlobalData2.ROOT_DATA_TYPE_RECOMMENDATIONS);
		dataRecommendations.setValue(recommendationsCid.toSafeString());
		DataReference dataDescription = new DataReference();
		dataDescription.setType(GlobalData2.ROOT_DATA_TYPE_DESCRIPTION);
		dataDescription.setValue(descriptionCid.toSafeString());
		root.getData().add(dataRecords);
		root.getData().add(dataRecommendations);
		root.getData().add(dataDescription);
		byte[] data_root = GlobalData2.serializeRoot(root);
		Assert.assertNotNull(data_root);
		IpfsFile rootCid = _generateHash(data_root);
		Assert.assertEquals(IpfsFile.fromIpfsCid("QmXWb8sYdQXTTX8BoM3oVzxnvCbx3NDCQVD774TuMEVR59"), rootCid);
	}


	private static final IpfsKey K0 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14Y");
	private static final IpfsKey K1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	private static IpfsFile _generateHash(byte[] data)
	{
		int hashCode = Arrays.hashCode(data);
		byte[] hash = new byte[34];
		ByteBuffer buffer = ByteBuffer.wrap(hash);
		buffer.put((byte)18).put((byte)32);
		buffer.putInt(hashCode).putInt(hashCode).putInt(hashCode).putInt(hashCode);
		return IpfsFile.fromIpfsCid(Cid.cast(hash).toString());
	}
}
