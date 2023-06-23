
package com.jeffdisher.cacophony.data.global.v2.recommendations;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.jeffdisher.cacophony.data.global.v2.recommendations package. 
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

    private final static QName _Recommendations_QNAME = new QName("https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/recommendations2.xsd", "recommendations");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.jeffdisher.cacophony.data.global.v2.recommendations
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link CacophonyRecommendations }
     * 
     */
    public CacophonyRecommendations createCacophonyRecommendations() {
        return new CacophonyRecommendations();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CacophonyRecommendations }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/recommendations2.xsd", name = "recommendations")
    public JAXBElement<CacophonyRecommendations> createRecommendations(CacophonyRecommendations value) {
        return new JAXBElement<CacophonyRecommendations>(_Recommendations_QNAME, CacophonyRecommendations.class, null, value);
    }

}
