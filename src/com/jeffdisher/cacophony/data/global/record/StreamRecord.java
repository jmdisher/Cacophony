
package com.jeffdisher.cacophony.data.global.record;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StreamRecord complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="StreamRecord">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="description" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="publishedSecondsUtc" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="discussion" type="{http://www.w3.org/2001/XMLSchema}anyURI" minOccurs="0"/>
 *         &lt;element name="elements" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/record.xsd}DataArray"/>
 *         &lt;element name="publisherKey" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlRootElement(name="record")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StreamRecord", propOrder = {
    "name",
    "description",
    "publishedSecondsUtc",
    "discussion",
    "elements",
    "publisherKey"
})
public class StreamRecord {

    @XmlElement(required = true)
    protected String name;
    @XmlElement(required = true)
    protected String description;
    protected int publishedSecondsUtc;
    @XmlSchemaType(name = "anyURI")
    protected String discussion;
    @XmlElement(required = true)
    protected DataArray elements;
    @XmlElement(required = true)
    protected String publisherKey;

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
    public int getPublishedSecondsUtc() {
        return publishedSecondsUtc;
    }

    /**
     * Sets the value of the publishedSecondsUtc property.
     * 
     */
    public void setPublishedSecondsUtc(int value) {
        this.publishedSecondsUtc = value;
    }

    /**
     * Gets the value of the discussion property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDiscussion() {
        return discussion;
    }

    /**
     * Sets the value of the discussion property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDiscussion(String value) {
        this.discussion = value;
    }

    /**
     * Gets the value of the elements property.
     * 
     * @return
     *     possible object is
     *     {@link DataArray }
     *     
     */
    public DataArray getElements() {
        return elements;
    }

    /**
     * Sets the value of the elements property.
     * 
     * @param value
     *     allowed object is
     *     {@link DataArray }
     *     
     */
    public void setElements(DataArray value) {
        this.elements = value;
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

}
