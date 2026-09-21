package com.smartbox.investory.integrations.ksef;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parses KSeF FA(3) invoice XML into provider-neutral facts. Namespace-agnostic and independent of
 * any accounting-specific classification.
 */
@Component
public class Fa3InvoiceParser {

  public Fa3Invoice parse(byte[] xml) {
    if (xml == null || xml.length == 0) {
      throw new IllegalArgumentException("KSeF invoice XML is empty");
    }
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
      document.getDocumentElement().normalize();

      String reference = firstText(document, "P_2");
      LocalDate issueDate = date(firstText(document, "P_1"));
      LocalDate saleDate = date(firstText(document, "P_6"));
      String currency = firstText(document, "KodWaluty");
      String invoiceType = firstText(document, "RodzajFaktury");
      String sellerNip = firstTextUnder(document, "Podmiot1", "NIP");
      String sellerName = firstTextUnder(document, "Podmiot1", "Nazwa");
      String buyerNip = firstTextUnder(document, "Podmiot2", "NIP");
      String buyerName = firstTextUnder(document, "Podmiot2", "Nazwa");

      BigDecimal gross = decimal(firstText(document, "P_15"));
      BigDecimal net = sumNumberedFields(document, "P_13_");
      BigDecimal vat = sumNumberedFields(document, "P_14_");
      BigDecimal vatRate = explicitVatRate(document);

      if (net == null && gross != null && vat != null) net = gross.subtract(vat);
      if (vat == null && gross != null && net != null) vat = gross.subtract(net);
      if (gross == null && net != null && vat != null) gross = net.add(vat);

      return new Fa3Invoice(
          reference,
          issueDate,
          saleDate,
          sellerNip,
          sellerName,
          buyerNip,
          buyerName,
          currency,
          net,
          vat,
          gross,
          invoiceType,
          vatRate);
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Could not parse KSeF invoice XML: " + rootMessage(exception), exception);
    }
  }

  private BigDecimal sumNumberedFields(Document document, String prefix) {
    NodeList all = document.getElementsByTagNameNS("*", "*");
    BigDecimal sum = BigDecimal.ZERO;
    boolean found = false;
    for (int i = 0; i < all.getLength(); i++) {
      Node node = all.item(i);
      String localName = node.getLocalName();
      if (localName != null && localName.startsWith(prefix)) {
        BigDecimal value = decimal(node.getTextContent());
        if (value != null) {
          sum = sum.add(value);
          found = true;
        }
      }
    }
    return found ? sum : null;
  }

  /** Returns a rate only when every KSeF line uses the same explicit numeric rate. */
  private BigDecimal explicitVatRate(Document document) {
    NodeList nodes = document.getElementsByTagNameNS("*", "P_12");
    Set<BigDecimal> rates = new HashSet<>();
    boolean nonNumericRate = false;
    for (int i = 0; i < nodes.getLength(); i++) {
      String value = clean(nodes.item(i).getTextContent());
      BigDecimal rate = decimal(value);
      if (rate != null) rates.add(rate.stripTrailingZeros());
      else if (value != null) nonNumericRate = true;
    }
    return !nonNumericRate && rates.size() == 1 ? rates.iterator().next() : null;
  }

  private String firstText(Document document, String localName) {
    NodeList nodes = document.getElementsByTagNameNS("*", localName);
    for (int i = 0; i < nodes.getLength(); i++) {
      String value = clean(nodes.item(i).getTextContent());
      if (value != null) return value;
    }
    return null;
  }

  private String firstTextUnder(Document document, String parentLocalName, String childLocalName) {
    NodeList parents = document.getElementsByTagNameNS("*", parentLocalName);
    if (parents.getLength() == 0) return null;
    Node parent = parents.item(0);
    List<Node> stack = new ArrayList<>();
    stack.add(parent);
    while (!stack.isEmpty()) {
      Node current = stack.remove(stack.size() - 1);
      if (childLocalName.equals(current.getLocalName())) {
        String value = clean(current.getTextContent());
        if (value != null) return value;
      }
      NodeList children = current.getChildNodes();
      for (int i = children.getLength() - 1; i >= 0; i--) {
        stack.add(children.item(i));
      }
    }
    return null;
  }

  private BigDecimal decimal(String value) {
    String clean = clean(value);
    if (clean == null) return null;
    try {
      return new BigDecimal(clean.replace(" ", "").replace(',', '.'));
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private LocalDate date(String value) {
    String clean = clean(value);
    if (clean == null) return null;
    try {
      return LocalDate.parse(clean);
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private String clean(String value) {
    if (value == null) return null;
    String clean = value.trim();
    return clean.isBlank() ? null : clean;
  }

  private String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) current = current.getCause();
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }
}
