
package com.jeffdisher.cacophony.data.global.record;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.jeffdisher.cacophony.data.global.record package. 
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

    private final static QName _Record_QNAME = new QName("https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/record.xsd", "record");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.jeffdisher.cacophony.data.global.record
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link StreamRecord }
     * 
     */
    public StreamRecord createStreamRecord() {
        return new StreamRecord();
    }

    /**
     * Create an instance of {@link DataElement }
     * 
     */
    public DataElement createDataElement() {
        return new DataElement();
    }

    /**
     * Create an instance of {@link DataArray }
     * 
     */
    public DataArray createDataArray() {
        return new DataArray();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StreamRecord }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/record.xsd", name = "record")
    public JAXBElement<StreamRecord> createRecord(StreamRecord value) {
        return new JAXBElement<StreamRecord>(_Record_QNAME, StreamRecord.class, null, value);
    }

}
