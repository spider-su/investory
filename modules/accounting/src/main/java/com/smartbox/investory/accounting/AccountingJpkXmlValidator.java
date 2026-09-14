package com.smartbox.investory.accounting;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/** Validates generated JPK_V7M(3) XML against the locally bundled official schema. */
@Component
public class AccountingJpkXmlValidator {
  private static final String SCHEMA = "/jpk/jpk-v7m-3/schemat.xsd";

  public void validate(byte[] payload) {
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      // Imports resolve only to the bundled sibling XSDs; network and nested-jar access remain
      // blocked.
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setResourceResolver(new BundledSchemaResolver());
      var schemaUrl = getClass().getResource(SCHEMA);
      try (InputStream schema = getClass().getResourceAsStream(SCHEMA)) {
        if (schema == null) throw new IllegalStateException("Bundled JPK_V7M(3) schema is missing");
        var source = new StreamSource(schema);
        source.setSystemId(schemaUrl.toExternalForm());
        factory
            .newSchema(source)
            .newValidator()
            .validate(new StreamSource(new ByteArrayInputStream(payload)));
      }
    } catch (Exception exception) {
      throw new IllegalStateException("JPK_VALIDATION_FAILED", exception);
    }
  }

  private static final class BundledSchemaResolver implements LSResourceResolver {
    @Override
    public LSInput resolveResource(
        String type, String namespaceURI, String publicId, String systemId, String baseURI) {
      String name = systemId.substring(systemId.lastIndexOf('/') + 1);
      String resource = "/jpk/jpk-v7m-3/" + name;
      InputStream stream = AccountingJpkXmlValidator.class.getResourceAsStream(resource);
      if (stream == null) return null;
      return new BundledSchemaInput(publicId, systemId, stream);
    }
  }

  private static final class BundledSchemaInput implements LSInput {
    private final String publicId;
    private final String systemId;
    private final InputStream byteStream;

    private BundledSchemaInput(String publicId, String systemId, InputStream byteStream) {
      this.publicId = publicId;
      this.systemId = systemId;
      this.byteStream = byteStream;
    }

    @Override
    public Reader getCharacterStream() {
      return null;
    }

    @Override
    public void setCharacterStream(Reader characterStream) {}

    @Override
    public InputStream getByteStream() {
      return byteStream;
    }

    @Override
    public void setByteStream(InputStream byteStream) {}

    @Override
    public String getStringData() {
      return null;
    }

    @Override
    public void setStringData(String stringData) {}

    @Override
    public String getSystemId() {
      return systemId;
    }

    @Override
    public void setSystemId(String systemId) {}

    @Override
    public String getPublicId() {
      return publicId;
    }

    @Override
    public void setPublicId(String publicId) {}

    @Override
    public String getBaseURI() {
      return null;
    }

    @Override
    public void setBaseURI(String baseURI) {}

    @Override
    public String getEncoding() {
      return null;
    }

    @Override
    public void setEncoding(String encoding) {}

    @Override
    public boolean getCertifiedText() {
      return false;
    }

    @Override
    public void setCertifiedText(boolean certifiedText) {}
  }
}
