
package com.jeffdisher.cacophony.data.global.v2.record;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.jeffdisher.cacophony.data.global.v2.record package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _Record_QNAME = new QName("https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd", "record");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.jeffdisher.cacophony.data.global.v2.record
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link CacophonyRecord }
     * 
     */
    public CacophonyRecord createCacophonyRecord() {
        return new CacophonyRecord();
    }

    /**
     * Create an instance of {@link ExtensionReference }
     * 
     */
    public ExtensionReference createExtensionReference() {
        return new ExtensionReference();
    }

    /**
     * Create an instance of {@link ThumbnailReference }
     * 
     */
    public ThumbnailReference createThumbnailReference() {
        return new ThumbnailReference();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CacophonyRecord }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd", name = "record")
    public JAXBElement<CacophonyRecord> createRecord(CacophonyRecord value) {
        return new JAXBElement<CacophonyRecord>(_Record_QNAME, CacophonyRecord.class, null, value);
    }

}
