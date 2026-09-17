package com.smartbox.investory.accounting;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Renders a reviewed DRA into the unsigned KEDU XML representation accepted by ZUS tooling. */
public final class ZusDraKeduRenderer {
  public static final String NAMESPACE = "http://www.zus.pl/2026/KEDU_5_7";

  private final ZusDraValidator validator = new ZusDraValidator();

  public byte[] render(ZusDraDeclaration declaration) {
    validator.validate(declaration);
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Document document = factory.newDocumentBuilder().newDocument();
      Element root = element(document, "KEDU");
      root.setAttribute("wersja_schematu", "1");
      document.appendChild(root);
      Element header = child(document, root, "naglowek.KEDU");
      Element program = child(document, header, "program");
      text(document, program, "producent", "Investory");
      text(document, program, "symbol", "INVESTORY_ZUS_DRA");
      text(document, program, "wersja", "0.1");
      Element dra = child(document, root, "ZUSDRA");
      dra.setAttribute("id_dokumentu", "1");
      dra.setAttribute("kolejnosc", "1");
      renderI(document, dra, declaration);
      renderPayer(document, dra, declaration.payer());
      renderIII(document, dra, declaration);
      renderValues(document, dra, "IV", declaration.socialSummary());
      renderValues(document, dra, "V", declaration.benefits());
      renderValues(document, dra, "VI", declaration.healthSummary());
      renderValues(document, dra, "VIII", declaration.fep());
      renderX(document, dra, declaration.income());
      renderValues(document, dra, "XI", declaration.monthlyHealthTaxation());
      renderValues(document, dra, "XII", declaration.annualHealthSettlement());
      if (declaration.declarationDate() != null) {
        Element xiii = child(document, dra, "XIII");
        text(document, xiii, "p1", date(declaration.declarationDate()));
      }
      var output = new ByteArrayOutputStream();
      var transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.transform(new DOMSource(document), new StreamResult(output));
      return output.toByteArray();
    } catch (Exception exception) {
      throw new IllegalStateException("ZUS_DRA_KEDU_RENDER_FAILED", exception);
    }
  }

  private void renderI(Document d, Element dra, ZusDraDeclaration value) {
    Element i = child(d, dra, "I");
    text(d, i, "p1", value.submissionDeadlineCode());
    Element p2 = child(d, i, "p2");
    text(d, p2, "p1", value.submissionNumber());
    text(d, p2, "p2", YearMonth.from(value.period()).toString());
  }

  private void renderPayer(Document d, Element dra, ZusDraDeclaration.Payer payer) {
    Element ii = child(d, dra, "II");
    textIfPresent(d, ii, "p1", payer.nip());
    textIfPresent(d, ii, "p2", payer.regon());
    textIfPresent(d, ii, "p3", payer.pesel());
    textIfPresent(d, ii, "p4", payer.documentType());
    textIfPresent(d, ii, "p5", payer.documentNumber());
    textIfPresent(d, ii, "p6", payer.shortName());
    textIfPresent(d, ii, "p7", payer.surname());
    textIfPresent(d, ii, "p8", payer.firstName());
    if (payer.birthDate() != null) text(d, ii, "p9", date(payer.birthDate()));
  }

  private void renderIII(Document d, Element dra, ZusDraDeclaration value) {
    Element iii = child(d, dra, "III");
    text(d, iii, "p1", Integer.toString(value.insuredCount()));
    text(d, iii, "p3", value.accidentRate().setScale(2).toPlainString());
  }

  private void renderX(Document d, Element dra, ZusDraDeclaration.IncomeDeclaration value) {
    Element x = child(d, dra, "X");
    renderInsuranceTitle(d, x, value.insuranceTitle());
    textIfPresent(d, x, "p2", amount(value.socialBase()));
    textIfPresent(d, x, "p3", amount(value.sicknessBase()));
    textIfPresent(d, x, "p4", amount(value.accidentBase()));
    textIfPresent(d, x, "p5", amount(value.healthBase()));
    textIfPresent(d, x, "p6", value.annualBaseExceeded());
  }

  private void renderInsuranceTitle(Document d, Element x, String value) {
    if (value == null || value.isBlank()) return;
    String digits = value.replace(" ", "");
    if (!digits.matches("\\d{6}")) {
      text(d, x, "p1", value);
      return;
    }
    Element code = child(d, x, "p1");
    text(d, code, "p1", digits.substring(0, 4));
    text(d, code, "p2", digits.substring(4, 5));
    text(d, code, "p3", digits.substring(5));
  }

  private void renderValues(Document d, Element parent, String name, List<String> values) {
    if (values.isEmpty()) return;
    Element section = child(d, parent, name);
    for (int index = 0; index < values.size(); index++)
      textIfPresent(d, section, "p" + (index + 1), values.get(index));
  }

  private static String amount(BigDecimal value) {
    return value == null ? null : value.setScale(2).toPlainString();
  }

  private static String date(LocalDate value) {
    return value.toString();
  }

  private static Element element(Document d, String name) {
    return d.createElementNS(NAMESPACE, name);
  }

  private static Element child(Document d, Element parent, String name) {
    Element child = element(d, name);
    parent.appendChild(child);
    return child;
  }

  private static void text(Document d, Element parent, String name, String value) {
    Element child = child(d, parent, name);
    child.appendChild(d.createTextNode(value));
  }

  private static void textIfPresent(Document d, Element parent, String name, String value) {
    if (value != null && !value.isBlank()) text(d, parent, name, value);
  }
}
