package com.smartbox.investory.accounting;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

/** JPK_V7M(3) projection. It consumes FilingResult and performs no tax calculation. */
@Service
public class AccountingJpkGenerator {
  static final String NS = "http://crd.gov.pl/wzor/2025/12/19/14090/";

  public byte[] generate(AccountingFilingService.FilingResult result) {
    AccountingMonthSnapshot s = result.snapshot();
    AccountingProfile p = result.profile();
    StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<JPK xmlns=\"")
        .append(NS)
        .append(
            "\" xmlns:etd=\"http://crd.gov.pl/xml/schematy/dziedzinowe/mf/2022/09/13/eD/DefinicjeTypy/\">\n");
    xml.append(
            "<Naglowek><KodFormularza kodSystemowy=\"JPK_V7M (3)\" wersjaSchemy=\"1-0E\">JPK_VAT</KodFormularza><WariantFormularza>3</WariantFormularza><DataWytworzeniaJPK>")
        .append(OffsetDateTime.now())
        .append(
            "</DataWytworzeniaJPK><NazwaSystemu>Investory Accounting POC</NazwaSystemu><CelZlozenia poz=\"P_7\">1</CelZlozenia><KodUrzedu>")
        .append(escape(p.taxOfficeCode()))
        .append("</KodUrzedu><Rok>")
        .append(s.period().getYear())
        .append("</Rok><Miesiac>")
        .append(s.period().getMonthValue())
        .append("</Miesiac></Naglowek>\n");
    xml.append("<Podmiot1 rola=\"Podatnik\"><OsobaNiefizyczna><etd:NIP>")
        .append(escape(p.nip()))
        .append("</etd:NIP><PelnaNazwa>")
        .append(escape(p.fullName()))
        .append("</PelnaNazwa><Email>")
        .append(escape(p.email()))
        .append("</Email></OsobaNiefizyczna></Podmiot1>\n");
    xml.append(
            "<Deklaracja><Naglowek><KodFormularzaDekl kodSystemowy=\"VAT-7 (23)\" kodPodatku=\"VAT\" rodzajZobowiazania=\"Z\" wersjaSchemy=\"1-0E\">VAT-7</KodFormularzaDekl><WariantFormularzaDekl>23</WariantFormularzaDekl></Naglowek><PozycjeSzczegolowe><P_38>")
        .append(money(s.vat().outputVatAfterSalesCorrection()))
        .append("</P_38><P_41>")
        .append(money(s.vat().deductibleInputVat()))
        .append("</P_41><P_51>")
        .append(money(s.vat().calculatedVat()))
        .append("</P_51></PozycjeSzczegolowe><Pouczenia>1</Pouczenia></Deklaracja>\n");
    xml.append("<Ewidencja>");
    int i = 1;
    for (var row : s.invoices()) {
      if ("SALES_INVOICE".equals(row.invoiceKind()))
        xml.append("<SprzedazWiersz><LpSprzedazy>")
            .append(i++)
            .append("</LpSprzedazy><NrKontrahenta>UNKNOWN</NrKontrahenta><NazwaKontrahenta>")
            .append(escape(row.customerAlias()))
            .append("</NazwaKontrahenta><DowodSprzedazy>")
            .append(escape(row.reference()))
            .append("</DowodSprzedazy><DataWystawienia>")
            .append(row.issueDate())
            .append("</DataWystawienia>")
            .append(
                row.saleDate() == null
                    ? ""
                    : "<DataSprzedazy>" + row.saleDate() + "</DataSprzedazy>")
            .append("<BFK>1</BFK><K_19>")
            .append(money(row.netAmount()))
            .append("</K_19><K_20>")
            .append(money(row.vatAmount()))
            .append("</K_20></SprzedazWiersz>");
    }
    xml.append("<SprzedazCtrl><LiczbaWierszySprzedazy>")
        .append(i - 1)
        .append("</LiczbaWierszySprzedazy><PodatekNalezny>")
        .append(money(s.vat().outputVatAfterSalesCorrection()))
        .append("</PodatekNalezny></SprzedazCtrl>");
    int j = 1;
    for (var row : s.expenses())
      xml.append("<ZakupWiersz><LpZakupu>")
          .append(j++)
          .append("</LpZakupu><NrDostawcy>UNKNOWN</NrDostawcy><NazwaDostawcy>")
          .append(escape(row.supplierAlias()))
          .append("</NazwaDostawcy><DowodZakupu>")
          .append(escape(row.reference()))
          .append("</DowodZakupu><DataZakupu>")
          .append(row.invoiceDate())
          .append("</DataZakupu><BFK>1</BFK><K_42>")
          .append(money(row.netAmount()))
          .append("</K_42><K_43>")
          .append(money(row.vatAmount()))
          .append("</K_43></ZakupWiersz>");
    xml.append("<ZakupCtrl><LiczbaWierszyZakupow>")
        .append(j - 1)
        .append("</LiczbaWierszyZakupow><PodatekNaliczony>")
        .append(money(s.vat().deductibleInputVat()))
        .append("</PodatekNaliczony></ZakupCtrl></Ewidencja></JPK>");
    return xml.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String money(java.math.BigDecimal value) {
    return value == null ? "0" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
  }

  private String escape(String value) {
    if (value == null) return "";
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
