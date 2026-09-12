package com.smartbox.investory.accounting;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.springframework.stereotype.Component;

/** Validates generated JPK_V7M(3) XML against the locally bundled official schema. */
@Component
public class AccountingJpkXmlValidator {
  private static final String SCHEMA = "/jpk/jpk-v7m-3/schemat.xsd";

  public void validate(byte[] payload) {
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      // Imports resolve only to the bundled sibling XSDs; network schemes remain blocked.
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
      var schemaUrl = getClass().getResource(SCHEMA);
      try (InputStream schema = getClass().getResourceAsStream(SCHEMA)) {
        if (schema == null) throw new IllegalStateException("Bundled JPK_V7M(3) schema is missing");
        var source = new StreamSource(schema);
        source.setSystemId(schemaUrl.toExternalForm());
        factory.newSchema(source).newValidator().validate(
            new StreamSource(new ByteArrayInputStream(payload)));
      }
    } catch (Exception exception) {
      throw new IllegalStateException("JPK_VALIDATION_FAILED", exception);
    }
  }
}
