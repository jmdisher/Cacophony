
package com.jeffdisher.cacophony.data.global.v2.description;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CacophonyDescription complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CacophonyDescription">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="name" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/description2.xsd}SmallString"/>
 *         &lt;element name="description" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/description2.xsd}LongString" minOccurs="0"/>
 *         &lt;element name="picture" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/description2.xsd}PictureReference" minOccurs="0"/>
 *         &lt;element name="feature" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/description2.xsd}IpfsCid" minOccurs="0"/>
 *         &lt;element name="misc" type="{https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/description2.xsd}MiscData" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CacophonyDescription", propOrder = {
    "name",
    "description",
    "picture",
    "feature",
    "misc"
})
public class CacophonyDescription {

    @XmlElement(required = true)
    protected String name;
    protected String description;
    protected PictureReference picture;
    protected String feature;
    protected List<MiscData> misc;

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
     * Gets the value of the picture property.
     * 
     * @return
     *     possible object is
     *     {@link PictureReference }
     *     
     */
    public PictureReference getPicture() {
        return picture;
    }

    /**
     * Sets the value of the picture property.
     * 
     * @param value
     *     allowed object is
     *     {@link PictureReference }
     *     
     */
    public void setPicture(PictureReference value) {
        this.picture = value;
    }

    /**
     * Gets the value of the feature property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFeature() {
        return feature;
    }

    /**
     * Sets the value of the feature property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFeature(String value) {
        this.feature = value;
    }

    /**
     * Gets the value of the misc property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the misc property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getMisc().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link MiscData }
     * 
     * 
     */
    public List<MiscData> getMisc() {
        if (misc == null) {
            misc = new ArrayList<MiscData>();
        }
        return this.misc;
    }

}
