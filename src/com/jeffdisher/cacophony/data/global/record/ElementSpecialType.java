
package com.jeffdisher.cacophony.data.global.record;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ElementSpecialType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ElementSpecialType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="image"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "ElementSpecialType")
@XmlEnum
public enum ElementSpecialType {

    @XmlEnumValue("image")
    IMAGE("image");
    private final String value;

    ElementSpecialType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ElementSpecialType fromValue(String v) {
        for (ElementSpecialType c: ElementSpecialType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
