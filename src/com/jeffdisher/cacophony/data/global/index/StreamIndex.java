
package com.jeffdisher.cacophony.data.global.index;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StreamIndex complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="StreamIndex">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="version" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="description" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/index.xsd}IpfsCid"/>
 *         &lt;element name="recommendations" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/index.xsd}IpfsCid"/>
 *         &lt;element name="records" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/index.xsd}IpfsCid"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlRootElement(name="index")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StreamIndex", propOrder = {
    "version",
    "description",
    "recommendations",
    "records"
})
public class StreamIndex {

    protected int version;
    @XmlElement(required = true)
    protected String description;
    @XmlElement(required = true)
    protected String recommendations;
    @XmlElement(required = true)
    protected String records;

    /**
     * Gets the value of the version property.
     * 
     */
    public int getVersion() {
        return version;
    }

    /**
     * Sets the value of the version property.
     * 
     */
    public void setVersion(int value) {
        this.version = value;
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
     * Gets the value of the recommendations property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRecommendations() {
        return recommendations;
    }

    /**
     * Sets the value of the recommendations property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRecommendations(String value) {
        this.recommendations = value;
    }

    /**
     * Gets the value of the records property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRecords() {
        return records;
    }

    /**
     * Sets the value of the records property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRecords(String value) {
        this.records = value;
    }

}
