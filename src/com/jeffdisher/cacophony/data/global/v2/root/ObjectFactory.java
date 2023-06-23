
package com.jeffdisher.cacophony.data.global.v2.root;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.jeffdisher.cacophony.data.global.v2.root package. 
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

    private final static QName _Root_QNAME = new QName("https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/root2.xsd", "root");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.jeffdisher.cacophony.data.global.v2.root
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link CacophonyRoot }
     * 
     */
    public CacophonyRoot createCacophonyRoot() {
        return new CacophonyRoot();
    }

    /**
     * Create an instance of {@link DataReference }
     * 
     */
    public DataReference createDataReference() {
        return new DataReference();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CacophonyRoot }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/root2.xsd", name = "root")
    public JAXBElement<CacophonyRoot> createRoot(CacophonyRoot value) {
        return new JAXBElement<CacophonyRoot>(_Root_QNAME, CacophonyRoot.class, null, value);
    }

}
