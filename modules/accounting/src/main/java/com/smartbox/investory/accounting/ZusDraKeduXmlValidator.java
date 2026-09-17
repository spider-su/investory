package com.smartbox.investory.accounting;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/** Validates unsigned KEDU XML against the bundled ZUS KEDU schema. */
public final class ZusDraKeduXmlValidator {
  public void validate(byte[] payload) {
    try (InputStream schema = getClass().getResourceAsStream("/zus/kedu-5.7/kedu.xsd")) {
      if (schema == null) throw new IllegalStateException("Bundled ZUS KEDU schema is missing");
      var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setResourceResolver(new BundledResolver());
      factory
          .newSchema(new StreamSource(schema))
          .newValidator()
          .validate(new StreamSource(new ByteArrayInputStream(payload)));
    } catch (Exception exception) {
      throw new IllegalStateException("ZUS_DRA_KEDU_SCHEMA_VALIDATION_FAILED", exception);
    }
  }

  private static final class BundledResolver implements LSResourceResolver {
    @Override
    public LSInput resolveResource(
        String type, String namespaceUri, String publicId, String systemId, String baseUri) {
      if (systemId == null || !systemId.contains("xmldsig-core-schema.xsd")) return null;
      InputStream stream =
          ZusDraKeduXmlValidator.class.getResourceAsStream("/zus/kedu-5.7/xmldsig-core-schema.xsd");
      return new BundledInput(publicId, systemId, stream);
    }
  }

  private static final class BundledInput implements LSInput {
    private final String publicId;
    private final String systemId;
    private final InputStream stream;

    private BundledInput(String publicId, String systemId, InputStream stream) {
      this.publicId = publicId;
      this.systemId = systemId;
      this.stream = stream;
    }

    public Reader getCharacterStream() {
      return null;
    }

    public void setCharacterStream(Reader value) {}

    public InputStream getByteStream() {
      return stream;
    }

    public void setByteStream(InputStream value) {}

    public String getStringData() {
      return null;
    }

    public void setStringData(String value) {}

    public String getSystemId() {
      return systemId;
    }

    public void setSystemId(String value) {}

    public String getPublicId() {
      return publicId;
    }

    public void setPublicId(String value) {}

    public String getBaseURI() {
      return null;
    }

    public void setBaseURI(String value) {}

    public String getEncoding() {
      return "UTF-8";
    }

    public void setEncoding(String value) {}

    public boolean getCertifiedText() {
      return false;
    }

    public void setCertifiedText(boolean value) {}
  }
}
