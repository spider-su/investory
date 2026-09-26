package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.application.port.RyczaltProfileReader;
import com.smartbox.investory.ryczalt.application.query.RyczaltInvoiceReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Generates a JPK_V7M XML projection from the current native Ryczalt facts. */
@Service
public class RyczaltJpkService {
  private static final String NAMESPACE = "http://crd.gov.pl/wzor/2025/12/19/14090/";

  private final RyczaltAccountingApi accounting;
  private final RyczaltProfileReader profiles;
  private final Clock clock;

  public RyczaltJpkService(
      RyczaltAccountingApi accounting, RyczaltProfileReader profiles, Clock clock) {
    this.accounting = accounting;
    this.profiles = profiles;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Document generate(long profileId, YearMonth month) {
    RyczaltProfile profile = profiles.read(profileId);
    RyczaltPeriodReadModel period = accounting.period(profileId, month);
    if (!"COMPLETE".equals(period.completeness().status())) {
      throw new IllegalStateException("Accounting period is incomplete: " + month);
    }
    validate(profile);
    List<RyczaltInvoiceReadModel> invoices = accounting.invoices(profileId, month);
    return new Document(
        "JPK_V7M_%d_%s.xml".formatted(profileId, month), render(profile, period, invoices));
  }

  private byte[] render(
      RyczaltProfile profile,
      RyczaltPeriodReadModel period,
      List<RyczaltInvoiceReadModel> invoices) {
    StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<JPK xmlns=\"")
        .append(NAMESPACE)
        .append(
            "\" xmlns:etd=\"http://crd.gov.pl/xml/schematy/dziedzinowe/mf/2022/09/13/eD/DefinicjeTypy/\">\n")
        .append(
            "<Naglowek><KodFormularza kodSystemowy=\"JPK_V7M (3)\" wersjaSchemy=\"1-0E\">JPK_VAT</KodFormularza>")
        .append("<WariantFormularza>3</WariantFormularza><DataWytworzeniaJPK>")
        .append(OffsetDateTime.now(clock.withZone(ZoneOffset.UTC)))
        .append("</DataWytworzeniaJPK><NazwaSystemu>Investory</NazwaSystemu>")
        .append("<CelZlozenia poz=\"P_7\">1</CelZlozenia><KodUrzedu>")
        .append(escape(profile.taxOfficeCode()))
        .append("</KodUrzedu><Rok>")
        .append(period.month().getYear())
        .append("</Rok><Miesiac>")
        .append(period.month().getMonthValue())
        .append("</Miesiac></Naglowek>\n")
        .append("<Podmiot1 rola=\"Podatnik\"><OsobaNiefizyczna><etd:NIP>")
        .append(escape(profile.nip()))
        .append("</etd:NIP><PelnaNazwa>")
        .append(escape(profile.fullName()))
        .append("</PelnaNazwa><Email>")
        .append(escape(profile.email()))
        .append("</Email></OsobaNiefizyczna></Podmiot1>\n")
        .append(
            "<Deklaracja><Naglowek><KodFormularzaDekl kodSystemowy=\"VAT-7 (23)\" kodPodatku=\"VAT\" rodzajZobowiazania=\"Z\" wersjaSchemy=\"1-0E\">VAT-7</KodFormularzaDekl><WariantFormularzaDekl>23</WariantFormularzaDekl></Naglowek>")
        .append("<PozycjeSzczegolowe><P_38>")
        .append(money(period.audit().outputVat()))
        .append("</P_38><P_41>")
        .append(money(period.audit().inputVat()))
        .append("</P_41><P_51>")
        .append(money(period.audit().finalPayable()))
        .append("</P_51></PozycjeSzczegolowe><Pouczenia>1</Pouczenia></Deklaracja>\n<Ewidencja>");

    int sales = 0;
    for (RyczaltInvoiceReadModel invoice : invoices) {
      if (invoice.direction().name().equals("INCOME")) {
        sales++;
        xml.append("<SprzedazWiersz><LpSprzedazy>")
            .append(sales)
            .append("</LpSprzedazy><NazwaKontrahenta>")
            .append(escape(counterparty(invoice)))
            .append("</NazwaKontrahenta><DowodSprzedazy>")
            .append(escape(invoice.reference()))
            .append("</DowodSprzedazy><DataWystawienia>")
            .append(invoice.issueDate())
            .append("</DataWystawienia><DataSprzedazy>")
            .append(
                invoice.accountingDate() == null ? invoice.issueDate() : invoice.accountingDate())
            .append("</DataSprzedazy><K_19>")
            .append(money(pln(invoice)))
            .append("</K_19><K_20>")
            .append(money(invoice.vatAmount()))
            .append("</K_20></SprzedazWiersz>");
      }
    }
    xml.append("<SprzedazCtrl><LiczbaWierszySprzedazy>")
        .append(sales)
        .append("</LiczbaWierszySprzedazy><PodatekNalezny>")
        .append(money(period.audit().outputVat()))
        .append("</PodatekNalezny></SprzedazCtrl>");

    int purchases = 0;
    for (RyczaltInvoiceReadModel invoice : invoices) {
      if (invoice.direction().name().equals("COST")) {
        purchases++;
        xml.append("<ZakupWiersz><LpZakupu>")
            .append(purchases)
            .append("</LpZakupu><NazwaDostawcy>")
            .append(escape(counterparty(invoice)))
            .append("</NazwaDostawcy><DowodZakupu>")
            .append(escape(invoice.reference()))
            .append("</DowodZakupu><DataZakupu>")
            .append(invoice.issueDate())
            .append("</DataZakupu><K_42>")
            .append(money(pln(invoice)))
            .append("</K_42><K_43>")
            .append(
                money(
                    invoice.deductibleVat() == null
                        ? invoice.vatAmount()
                        : invoice.deductibleVat()))
            .append("</K_43></ZakupWiersz>");
      }
    }
    return xml.append("<ZakupCtrl><LiczbaWierszyZakupow>")
        .append(purchases)
        .append("</LiczbaWierszyZakupow><PodatekNaliczony>")
        .append(money(period.audit().inputVat()))
        .append("</PodatekNaliczony></ZakupCtrl></Ewidencja></JPK>")
        .toString()
        .getBytes(StandardCharsets.UTF_8);
  }

  private void validate(RyczaltProfile profile) {
    if (blank(profile.nip())
        || blank(profile.fullName())
        || blank(profile.taxOfficeCode())
        || blank(profile.email())) {
      throw new IllegalStateException("MISSING_TAXPAYER_CONFIGURATION");
    }
  }

  private BigDecimal pln(RyczaltInvoiceReadModel invoice) {
    return invoice.bookedNetPln() == null ? invoice.netAmount() : invoice.bookedNetPln();
  }

  private String counterparty(RyczaltInvoiceReadModel invoice) {
    if (invoice.counterparty() == null) return "";
    return invoice.counterparty().alias() == null || invoice.counterparty().alias().isBlank()
        ? invoice.counterparty().legalName()
        : invoice.counterparty().alias();
  }

  private String money(BigDecimal value) {
    return value == null
        ? "0.00"
        : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
  }

  private String escape(String value) {
    if (value == null) return "";
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  public record Document(String filename, byte[] content) {}
}
