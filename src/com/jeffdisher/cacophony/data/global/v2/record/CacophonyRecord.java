
package com.jeffdisher.cacophony.data.global.v2.record;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CacophonyRecord complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CacophonyRecord">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="name" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd}SmallString"/>
 *         &lt;element name="description" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd}LongString" minOccurs="0"/>
 *         &lt;element name="publishedSecondsUtc" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="discussionUrl" type="{http://www.w3.org/2001/XMLSchema}anyURI" minOccurs="0"/>
 *         &lt;element name="publisherKey" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd}IpfsPublicKey"/>
 *         &lt;element name="thumbnail" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd}ThumbnailReference" minOccurs="0"/>
 *         &lt;element name="extension" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd}ExtensionReference" minOccurs="0"/>
 *         &lt;element name="replyTo" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd}IpfsCid" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CacophonyRecord", propOrder = {
    "name",
    "description",
    "publishedSecondsUtc",
    "discussionUrl",
    "publisherKey",
    "thumbnail",
    "extension",
    "replyTo"
})
public class CacophonyRecord {

    @XmlElement(required = true)
    protected String name;
    protected String description;
    protected long publishedSecondsUtc;
    @XmlSchemaType(name = "anyURI")
    protected String discussionUrl;
    @XmlElement(required = true)
    protected String publisherKey;
    protected ThumbnailReference thumbnail;
    protected ExtensionReference extension;
    protected String replyTo;

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the description property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the value of the description property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDescription(String value) {
        this.description = value;
    }

    /**
     * Gets the value of the publishedSecondsUtc property.
     * 
     */
    public long getPublishedSecondsUtc() {
        return publishedSecondsUtc;
    }

    /**
     * Sets the value of the publishedSecondsUtc property.
     * 
     */
    public void setPublishedSecondsUtc(long value) {
        this.publishedSecondsUtc = value;
    }

    /**
     * Gets the value of the discussionUrl property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDiscussionUrl() {
        return discussionUrl;
    }

    /**
     * Sets the value of the discussionUrl property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDiscussionUrl(String value) {
        this.discussionUrl = value;
    }

    /**
     * Gets the value of the publisherKey property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPublisherKey() {
        return publisherKey;
    }

    /**
     * Sets the value of the publisherKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPublisherKey(String value) {
        this.publisherKey = value;
    }

    /**
     * Gets the value of the thumbnail property.
     * 
     * @return
     *     possible object is
     *     {@link ThumbnailReference }
     *     
     */
    public ThumbnailReference getThumbnail() {
        return thumbnail;
    }

    /**
     * Sets the value of the thumbnail property.
     * 
     * @param value
     *     allowed object is
     *     {@link ThumbnailReference }
     *     
     */
    public void setThumbnail(ThumbnailReference value) {
        this.thumbnail = value;
    }

    /**
     * Gets the value of the extension property.
     * 
     * @return
     *     possible object is
     *     {@link ExtensionReference }
     *     
     */
    public ExtensionReference getExtension() {
        return extension;
    }

    /**
     * Sets the value of the extension property.
     * 
     * @param value
     *     allowed object is
     *     {@link ExtensionReference }
     *     
     */
    public void setExtension(ExtensionReference value) {
        this.extension = value;
    }

    /**
     * Gets the value of the replyTo property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getReplyTo() {
        return replyTo;
    }

    /**
     * Sets the value of the replyTo property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setReplyTo(String value) {
        this.replyTo = value;
    }

}
