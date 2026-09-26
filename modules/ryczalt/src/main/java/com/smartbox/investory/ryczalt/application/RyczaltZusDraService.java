package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.application.port.RyczaltProfileReader;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import com.smartbox.investory.ryczalt.calculation.zus.ZusRules2026;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Generates an unsigned ZUS DRA KEDU draft from the current native month facts. */
@Service
public class RyczaltZusDraService {
  private static final String NAMESPACE = "http://www.zus.pl/2026/KEDU_5_7";

  private final RyczaltAccountingApi accounting;
  private final RyczaltProfileReader profiles;
  private final Clock clock;

  public RyczaltZusDraService(
      RyczaltAccountingApi accounting, RyczaltProfileReader profiles, Clock clock) {
    this.accounting = accounting;
    this.profiles = profiles;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Document generate(long profileId, YearMonth month) {
    RyczaltPeriodReadModel period = accounting.period(profileId, month);
    if (!"COMPLETE".equals(period.completeness().status())) {
      throw new IllegalStateException("Accounting period is incomplete: " + month);
    }
    RyczaltProfile profile = profiles.read(profileId);
    validate(profile);

    BigDecimal total = value(period.zusAmount());
    BigDecimal social =
        profile.hasUop() ? BigDecimal.ZERO : total.min(ZusRules2026.FULL_JDG_SOCIAL);
    BigDecimal health = total.subtract(social).max(BigDecimal.ZERO);
    String xml = render(profile, month, social, health, total);
    return new Document(
        "ZUS_DRA_%d_%s.xml".formatted(profileId, month), xml.getBytes(StandardCharsets.UTF_8));
  }

  private String render(
      RyczaltProfile profile,
      YearMonth month,
      BigDecimal social,
      BigDecimal health,
      BigDecimal total) {
    StringBuilder xml =
        new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<KEDU xmlns=\"")
            .append(NAMESPACE)
            .append("\" wersja_schematu=\"1\">")
            .append("<naglowek.KEDU><program><producent>Investory</producent>")
            .append(
                "<symbol>INVESTORY_ZUS_DRA</symbol><wersja>0.1</wersja></program></naglowek.KEDU>")
            .append("<ZUSDRA id_dokumentu=\"1\" kolejnosc=\"1\">")
            .append("<I><p1>6</p1><p2><p1>01</p1><p2>")
            .append(month)
            .append("</p2></p2></I>")
            .append("<II><p1>")
            .append(escape(profile.nip()))
            .append("</p1><p6>")
            .append(escape(profile.fullName()))
            .append("</p6><p7>")
            .append(escape(profile.surname()))
            .append("</p7><p8>")
            .append(escape(profile.firstName()))
            .append("</p8><p9>")
            .append(profile.dateOfBirth())
            .append("</p9></II>")
            .append("<III><p1>1</p1><p3>1.67</p3></III>")
            .append("<IV><p1>")
            .append(amount(social))
            .append("</p1></IV>")
            .append("<VI><p1>0.00</p1><p2>")
            .append(amount(health))
            .append("</p2><p3>0.00</p3><p4>0.00</p4><p5>")
            .append(amount(health))
            .append("</p5><p6>0.00</p6><p7>")
            .append(amount(health))
            .append("</p7></VI>")
            .append("<X><p1><p1>0510</p1><p2>0</p2><p3>0</p3></p1><p5>")
            .append(amount(health))
            .append("</p5></X>")
            .append("<XI><p15>")
            .append(amount(health))
            .append("</p15><p17>")
            .append(amount(total))
            .append("</p17></XI>")
            .append("<XIII><p1>")
            .append(clock.instant().toString().substring(0, 10))
            .append("</p1></XIII></ZUSDRA></KEDU>");
    return xml.toString();
  }

  private void validate(RyczaltProfile profile) {
    if (profile == null
        || blank(profile.nip())
        || blank(profile.fullName())
        || blank(profile.firstName())
        || blank(profile.surname())
        || profile.dateOfBirth() == null) {
      throw new IllegalStateException("MISSING_TAXPAYER_CONFIGURATION");
    }
  }

  private static BigDecimal value(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private static String amount(BigDecimal value) {
    return value.setScale(2).toPlainString();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  public record Document(String filename, byte[] content) {}
}
