package com.smartbox.investory.accounting;

import java.io.ByteArrayInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;

/** Local structural guard. Official XSD resources can be attached when licensed for distribution. */
@Component
public class AccountingJpkXmlValidator {
  public void validate(byte[] payload) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(payload)).getDocumentElement();
      if (!"JPK".equals(root.getLocalName()) || !AccountingJpkGenerator.NS.equals(root.getNamespaceURI())) {
        throw new IllegalArgumentException("Unexpected JPK root or namespace");
      }
    } catch (Exception exception) {
      throw new IllegalStateException("JPK_VALIDATION_FAILED", exception);
    }
  }
}
