package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/** JPK_V7M(3) projection. It consumes a filing-only input and performs no tax calculation. */
@Service
public class AccountingJpkGenerator {
  static final String NS = "http://crd.gov.pl/wzor/2025/12/19/14090/";

  public byte[] generate(AccountingFilingService.FilingResult result) {
    return generate(result.filingInput());
  }

  public byte[] generate(AccountingFilingInput input) {
    AccountingMonthSnapshot.VatCalculation vat = input.vat();
    AccountingProfile p = input.taxpayer();
    JpkTotals totals = totals(input);
    StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<JPK xmlns=\"")
        .append(NS)
        .append(
            "\" xmlns:etd=\"http://crd.gov.pl/xml/schematy/dziedzinowe/mf/2022/09/13/eD/DefinicjeTypy/\">\n");
    xml.append(
            "<Naglowek><KodFormularza kodSystemowy=\"JPK_V7M (3)\" wersjaSchemy=\"1-0E\">JPK_VAT</KodFormularza><WariantFormularza>3</WariantFormularza><DataWytworzeniaJPK>")
        .append(generationTimestamp(input.period()))
        .append(
            "</DataWytworzeniaJPK><NazwaSystemu>Investory Accounting POC</NazwaSystemu><CelZlozenia poz=\"P_7\">1</CelZlozenia><KodUrzedu>")
        .append(escape(p.taxOfficeCode()))
        .append("</KodUrzedu><Rok>")
        .append(input.period().getYear())
        .append("</Rok><Miesiac>")
        .append(input.period().getMonthValue())
        .append("</Miesiac></Naglowek>\n");
    xml.append("<Podmiot1 rola=\"Podatnik\"><OsobaFizyczna><etd:NIP>")
        .append(escape(p.nip()))
        .append("</etd:NIP><etd:ImiePierwsze>")
        .append(escape(p.firstName()))
        .append("</etd:ImiePierwsze><etd:Nazwisko>")
        .append(escape(p.surname()))
        .append("</etd:Nazwisko>")
        .append(
            p.dateOfBirth() == null
                ? ""
                : "<etd:DataUrodzenia>" + p.dateOfBirth() + "</etd:DataUrodzenia>")
        .append("<Email>")
        .append(escape(p.email()))
        .append("</Email>")
        .append("</OsobaFizyczna></Podmiot1>\n");
    xml.append(
            "<Deklaracja><Naglowek><KodFormularzaDekl kodSystemowy=\"VAT-7 (23)\" kodPodatku=\"VAT\" rodzajZobowiazania=\"Z\" wersjaSchemy=\"1-0E\">VAT-7</KodFormularzaDekl><WariantFormularzaDekl>23</WariantFormularzaDekl></Naglowek><PozycjeSzczegolowe><P_13>")
        .append(declarationMoney(salesNet(input.sales(), BigDecimal.ZERO)))
        .append("</P_13><P_15>")
        .append(declarationMoney(salesNet(input.sales(), new BigDecimal("5"))))
        .append("</P_15><P_16>")
        .append(declarationMoney(salesVat(input.sales(), new BigDecimal("5"))))
        .append("</P_16><P_17>")
        .append(declarationMoney(salesNet(input.sales(), new BigDecimal("8"))))
        .append("</P_17><P_18>")
        .append(declarationMoney(salesVat(input.sales(), new BigDecimal("8"))))
        .append("</P_18><P_19>")
        .append(declarationMoney(salesNet(input.sales(), new BigDecimal("23"))))
        .append("</P_19><P_20>")
        .append(declarationMoney(salesVat(input.sales(), new BigDecimal("23"))))
        .append("</P_20><P_27>")
        .append(
            declarationMoney(importNet(input.purchases(), VatTreatment.IMPORT_OF_SERVICES_NON_EU)))
        .append("</P_27><P_28>")
        .append(
            declarationMoney(importVat(input.purchases(), VatTreatment.IMPORT_OF_SERVICES_NON_EU)))
        .append("</P_28><P_29>")
        .append(declarationMoney(importNet(input.purchases(), VatTreatment.IMPORT_OF_SERVICES_EU)))
        .append("</P_29><P_30>")
        .append(declarationMoney(importVat(input.purchases(), VatTreatment.IMPORT_OF_SERVICES_EU)))
        .append("</P_30><P_38>")
        .append(declarationMoney(totals.outputVat()))
        .append("</P_38><P_40>0</P_40><P_41>0</P_41><P_42>")
        .append(declarationMoney(totals.purchaseNet()))
        .append("</P_42><P_43>")
        .append(declarationMoney(totals.deductibleInputVat()))
        .append("</P_43><P_51>")
        .append(
            declarationMoney(
                totals
                    .outputVat()
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .subtract(
                        totals.deductibleInputVat().setScale(0, java.math.RoundingMode.HALF_UP))))
        .append("</P_51></PozycjeSzczegolowe><Pouczenia>1</Pouczenia></Deklaracja>\n");
    xml.append("<Ewidencja>");
    int i = 1;
    for (var row : input.sales()) {
      xml.append("<SprzedazWiersz><LpSprzedazy>")
          .append(i++)
          .append("</LpSprzedazy><NrKontrahenta>")
          .append(escape(row.counterpartyIdentifier()))
          .append("</NrKontrahenta><NazwaKontrahenta>")
          .append(escape(row.counterpartyName()))
          .append("</NazwaKontrahenta><DowodSprzedazy>")
          .append(escape(row.reference()))
          .append("</DowodSprzedazy><DataWystawienia>")
          .append(row.issueDate())
          .append("</DataWystawienia>")
          .append(
              row.saleDate() == null ? "" : "<DataSprzedazy>" + row.saleDate() + "</DataSprzedazy>")
          .append(evidence(row.evidence()))
          .append(salesVatColumns(row))
          .append("</SprzedazWiersz>");
    }
    for (var row : input.purchases()) {
      if (!isImportOfServices(row)) continue;
      xml.append("<SprzedazWiersz><LpSprzedazy>")
          .append(i++)
          .append("</LpSprzedazy><NrKontrahenta>")
          .append(escape(row.counterpartyIdentifier()))
          .append("</NrKontrahenta><NazwaKontrahenta>")
          .append(escape(row.counterpartyName()))
          .append("</NazwaKontrahenta><DowodSprzedazy>")
          .append(escape(row.reference()))
          .append("</DowodSprzedazy><DataWystawienia>")
          .append(row.issueDate())
          .append("</DataWystawienia>")
          .append(
              row.saleDate() == null ? "" : "<DataSprzedazy>" + row.saleDate() + "</DataSprzedazy>")
          .append(evidence(row.evidence()))
          .append(importServiceColumns(row))
          .append("</SprzedazWiersz>");
    }
    xml.append("<SprzedazCtrl><LiczbaWierszySprzedazy>")
        .append(i - 1)
        .append("</LiczbaWierszySprzedazy><PodatekNalezny>")
        .append(money(totals.outputVat()))
        .append("</PodatekNalezny></SprzedazCtrl>");
    int j = 1;
    for (var row : input.purchases())
      xml.append("<ZakupWiersz><LpZakupu>")
          .append(j++)
          .append("</LpZakupu><NrDostawcy>")
          .append(escape(row.counterpartyIdentifier()))
          .append("</NrDostawcy><NazwaDostawcy>")
          .append(escape(row.counterpartyName()))
          .append("</NazwaDostawcy><DowodZakupu>")
          .append(escape(row.reference()))
          .append("</DowodZakupu><DataZakupu>")
          .append(row.purchaseDate())
          .append("</DataZakupu>")
          .append(evidence(row.evidence()))
          .append("<K_42>")
          .append(money(row.netAmount()))
          .append("</K_42><K_43>")
          .append(money(row.deductibleVat()))
          .append("</K_43></ZakupWiersz>");
    xml.append("<ZakupCtrl><LiczbaWierszyZakupow>")
        .append(j - 1)
        .append("</LiczbaWierszyZakupow><PodatekNaliczony>")
        .append(money(totals.deductibleInputVat()))
        .append("</PodatekNaliczony></ZakupCtrl></Ewidencja></JPK>");
    return xml.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String money(java.math.BigDecimal value) {
    return value == null ? "0" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
  }

  private String declarationMoney(java.math.BigDecimal value) {
    return value == null ? "0" : value.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
  }

  private String salesVatColumns(AccountingFilingInput.FilingDocument row) {
    String net = money(row.netAmount());
    String vat = money(row.vatAmount());
    if (row.treatment() == VatTreatment.VAT_EXEMPT) return "<K_10>" + net + "</K_10>";
    if (row.treatment() == VatTreatment.EU_B2B_REVERSE_CHARGE
        || row.treatment() == VatTreatment.NON_EU_B2B_OUTSIDE_POLAND)
      return "<K_11>" + net + "</K_11>";
    if (row.treatment() != VatTreatment.DOMESTIC_VAT) return "";
    if (row.vatRate() == null) return "";
    if (row.vatRate().compareTo(new java.math.BigDecimal("8")) == 0)
      return "<K_17>" + net + "</K_17><K_18>" + vat + "</K_18>";
    if (row.vatRate().compareTo(new java.math.BigDecimal("5")) == 0)
      return "<K_15>" + net + "</K_15><K_16>" + vat + "</K_16>";
    if (row.vatRate().signum() == 0) return "<K_13>" + net + "</K_13>";
    if (row.vatRate().compareTo(new java.math.BigDecimal("23")) == 0)
      return "<K_19>" + net + "</K_19><K_20>" + vat + "</K_20>";
    return "";
  }

  private boolean isImportOfServices(AccountingFilingInput.FilingDocument row) {
    return row.treatment() == VatTreatment.IMPORT_OF_SERVICES_EU
        || row.treatment() == VatTreatment.IMPORT_OF_SERVICES_NON_EU;
  }

  private int importServiceCount(java.util.List<AccountingFilingInput.FilingDocument> purchases) {
    return (int) purchases.stream().filter(this::isImportOfServices).count();
  }

  private String importServiceColumns(AccountingFilingInput.FilingDocument row) {
    String net = money(row.netAmount());
    String vat = money(row.vatAmount());
    return row.treatment() == VatTreatment.IMPORT_OF_SERVICES_EU
        ? "<K_29>" + net + "</K_29><K_30>" + vat + "</K_30>"
        : "<K_27>" + net + "</K_27><K_28>" + vat + "</K_28>";
  }

  private java.math.BigDecimal purchaseNet(
      java.util.List<AccountingFilingInput.FilingDocument> purchases) {
    return purchases.stream()
        .map(AccountingFilingInput.FilingDocument::netAmount)
        .filter(java.util.Objects::nonNull)
        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
  }

  private java.math.BigDecimal salesNet(
      java.util.List<AccountingFilingInput.FilingDocument> sales, java.math.BigDecimal rate) {
    return sales.stream()
        .filter(row -> row.treatment() == VatTreatment.DOMESTIC_VAT)
        .filter(
            row ->
                rate.compareTo(
                        java.util.Objects.requireNonNullElse(
                            row.vatRate(), new java.math.BigDecimal("-1")))
                    == 0)
        .map(AccountingFilingInput.FilingDocument::netAmount)
        .filter(java.util.Objects::nonNull)
        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
  }

  private java.math.BigDecimal salesVat(
      java.util.List<AccountingFilingInput.FilingDocument> sales, java.math.BigDecimal rate) {
    return sales.stream()
        .filter(row -> row.treatment() == VatTreatment.DOMESTIC_VAT)
        .filter(
            row ->
                rate.compareTo(
                        java.util.Objects.requireNonNullElse(
                            row.vatRate(), new java.math.BigDecimal("-1")))
                    == 0)
        .map(AccountingFilingInput.FilingDocument::vatAmount)
        .filter(java.util.Objects::nonNull)
        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
  }

  private JpkTotals totals(AccountingFilingInput input) {
    java.math.BigDecimal outputVat =
        input.sales().stream()
            .filter(row -> row.treatment() == VatTreatment.DOMESTIC_VAT)
            .map(AccountingFilingInput.FilingDocument::vatAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
            .add(importVat(input.purchases(), VatTreatment.IMPORT_OF_SERVICES_EU))
            .add(importVat(input.purchases(), VatTreatment.IMPORT_OF_SERVICES_NON_EU));
    java.math.BigDecimal purchaseNet = purchaseNet(input.purchases());
    java.math.BigDecimal deductible =
        input.purchases().stream()
            .map(AccountingFilingInput.FilingDocument::deductibleVat)
            .filter(java.util.Objects::nonNull)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    if (input.sales().isEmpty() && input.purchases().isEmpty()) {
      outputVat = input.vat().outputVatAfterSalesCorrection();
      deductible = input.vat().deductibleInputVat();
    }
    return new JpkTotals(outputVat, purchaseNet, deductible);
  }

  private record JpkTotals(
      java.math.BigDecimal outputVat,
      java.math.BigDecimal purchaseNet,
      java.math.BigDecimal deductibleInputVat) {}

  private java.math.BigDecimal importNet(
      java.util.List<AccountingFilingInput.FilingDocument> purchases, VatTreatment treatment) {
    return purchases.stream()
        .filter(row -> row.treatment() == treatment)
        .map(AccountingFilingInput.FilingDocument::netAmount)
        .filter(java.util.Objects::nonNull)
        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
  }

  private java.math.BigDecimal importVat(
      java.util.List<AccountingFilingInput.FilingDocument> purchases, VatTreatment treatment) {
    return purchases.stream()
        .filter(row -> row.treatment() == treatment)
        .map(AccountingFilingInput.FilingDocument::vatAmount)
        .filter(java.util.Objects::nonNull)
        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
  }

  private String generationTimestamp(LocalDate period) {
    LocalDate generationDate =
        period
            .withDayOfMonth(period.lengthOfMonth())
            .isBefore(LocalDate.of(2026, 2, 1))
            ? LocalDate.of(2026, 2, 1)
            : period.withDayOfMonth(period.lengthOfMonth());
    return generationDate
        .atStartOfDay()
        .atOffset(ZoneOffset.UTC)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
  }

  private String escape(String value) {
    if (value == null) return "";
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private String evidence(AccountingFilingEvidence evidence) {
    if (evidence == null || evidence.type() == null) return "";
    return switch (evidence.type()) {
      case KSEF -> "<NrKSeF>" + escape(evidence.ksefNumber()) + "</NrKSeF>";
      case OFF -> "<OFF>1</OFF>";
      case BFK -> "<BFK>1</BFK>";
      case DI -> "<DI>1</DI>";
    };
  }
}
