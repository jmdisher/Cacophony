
package com.jeffdisher.cacophony.data.global.index;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.jeffdisher.cacophony.data.global.index package. 
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

    private final static QName _Index_QNAME = new QName("https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/index.xsd", "index");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.jeffdisher.cacophony.data.global.index
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link StreamIndex }
     * 
     */
    public StreamIndex createStreamIndex() {
        return new StreamIndex();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StreamIndex }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/index.xsd", name = "index")
    public JAXBElement<StreamIndex> createIndex(StreamIndex value) {
        return new JAXBElement<StreamIndex>(_Index_QNAME, StreamIndex.class, null, value);
    }

}
