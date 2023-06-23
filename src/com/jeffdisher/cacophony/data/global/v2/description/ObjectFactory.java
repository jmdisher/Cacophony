
package com.jeffdisher.cacophony.data.global.v2.description;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.jeffdisher.cacophony.data.global.v2.description package. 
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

    private final static QName _Description_QNAME = new QName("https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/description2.xsd", "description");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.jeffdisher.cacophony.data.global.v2.description
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link CacophonyDescription }
     * 
     */
    public CacophonyDescription createCacophonyDescription() {
        return new CacophonyDescription();
    }

    /**
     * Create an instance of {@link PictureReference }
     * 
     */
    public PictureReference createPictureReference() {
        return new PictureReference();
    }

    /**
     * Create an instance of {@link MiscData }
     * 
     */
    public MiscData createMiscData() {
        return new MiscData();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CacophonyDescription }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/description2.xsd", name = "description")
    public JAXBElement<CacophonyDescription> createDescription(CacophonyDescription value) {
        return new JAXBElement<CacophonyDescription>(_Description_QNAME, CacophonyDescription.class, null, value);
    }

}
