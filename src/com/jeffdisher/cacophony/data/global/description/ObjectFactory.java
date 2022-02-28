
package com.jeffdisher.cacophony.data.global.description;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.jeffdisher.cacophony.data.global.description package. 
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

    private final static QName _Description_QNAME = new QName("https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/description.xsd", "description");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.jeffdisher.cacophony.data.global.description
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link StreamDescription }
     * 
     */
    public StreamDescription createStreamDescription() {
        return new StreamDescription();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StreamDescription }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/description.xsd", name = "description")
    public JAXBElement<StreamDescription> createDescription(StreamDescription value) {
        return new JAXBElement<StreamDescription>(_Description_QNAME, StreamDescription.class, null, value);
    }

}
