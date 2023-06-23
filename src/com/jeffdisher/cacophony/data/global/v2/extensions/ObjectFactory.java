
package com.jeffdisher.cacophony.data.global.v2.extensions;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.jeffdisher.cacophony.data.global.v2.extensions package. 
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

    private final static QName _Video_QNAME = new QName("https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/extensions2_video.xsd", "video");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.jeffdisher.cacophony.data.global.v2.extensions
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link CacophonyExtensionVideo }
     * 
     */
    public CacophonyExtensionVideo createCacophonyExtensionVideo() {
        return new CacophonyExtensionVideo();
    }

    /**
     * Create an instance of {@link VideoFormat }
     * 
     */
    public VideoFormat createVideoFormat() {
        return new VideoFormat();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CacophonyExtensionVideo }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/extensions2_video.xsd", name = "video")
    public JAXBElement<CacophonyExtensionVideo> createVideo(CacophonyExtensionVideo value) {
        return new JAXBElement<CacophonyExtensionVideo>(_Video_QNAME, CacophonyExtensionVideo.class, null, value);
    }

}
