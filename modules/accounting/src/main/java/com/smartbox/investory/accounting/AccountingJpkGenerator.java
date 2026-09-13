package com.smartbox.investory.accounting;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
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
            "<Deklaracja><Naglowek><KodFormularzaDekl kodSystemowy=\"VAT-7 (23)\" kodPodatku=\"VAT\" rodzajZobowiazania=\"Z\" wersjaSchemy=\"1-0E\">VAT-7</KodFormularzaDekl><WariantFormularzaDekl>23</WariantFormularzaDekl></Naglowek><PozycjeSzczegolowe><P_38>")
        .append(declarationMoney(vat.outputVatAfterSalesCorrection()))
        .append("</P_38><P_40>0</P_40><P_41>0</P_41><P_42>0</P_42><P_43>")
        .append(declarationMoney(vat.deductibleInputVat()))
        .append("</P_43><P_51>")
        .append(declarationMoney(vat.calculatedVat()))
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
          .append(importServiceColumns(row))
          .append("</SprzedazWiersz>");
    }
    xml.append("<SprzedazCtrl><LiczbaWierszySprzedazy>")
        .append(i - 1)
        .append("</LiczbaWierszySprzedazy><PodatekNalezny>")
        .append(money(vat.outputVatAfterSalesCorrection()))
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
        .append(money(vat.deductibleInputVat()))
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
    if (vatRate(row) == 8) return "<K_17>" + net + "</K_17><K_18>" + vat + "</K_18>";
    if (vatRate(row) == 5) return "<K_15>" + net + "</K_15><K_16>" + vat + "</K_16>";
    return "<K_19>" + net + "</K_19><K_20>" + vat + "</K_20>";
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

  private int vatRate(AccountingFilingInput.FilingDocument row) {
    if (row.netAmount() == null || row.netAmount().signum() == 0 || row.vatAmount() == null)
      return 23;
    return row.vatAmount()
        .multiply(java.math.BigDecimal.valueOf(100))
        .divide(row.netAmount(), 0, java.math.RoundingMode.HALF_UP)
        .intValue();
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
