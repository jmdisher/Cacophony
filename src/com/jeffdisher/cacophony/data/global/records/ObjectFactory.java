
package com.jeffdisher.cacophony.data.global.records;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.jeffdisher.cacophony.data.global.records package. 
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

    private final static QName _Records_QNAME = new QName("https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/records.xsd", "records");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.jeffdisher.cacophony.data.global.records
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link StreamRecords }
     * 
     */
    public StreamRecords createStreamRecords() {
        return new StreamRecords();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StreamRecords }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/records.xsd", name = "records")
    public JAXBElement<StreamRecords> createRecords(StreamRecords value) {
        return new JAXBElement<StreamRecords>(_Records_QNAME, StreamRecords.class, null, value);
    }

}
