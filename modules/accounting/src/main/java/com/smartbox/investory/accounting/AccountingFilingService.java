package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingFilingService {
  private final AccountingFactService factService;
  private final AccountingPocRepository repository;
  private final AccountingJpkGenerator jpkGenerator;
  private final AccountingDueDatePolicy dueDatePolicy;

  public FilingResult filing(LocalDate period) {
    AccountingMonthSnapshot snapshot = factService.snapshot(period);
    AccountingProfile profile = factService.accountingProfile();
    String hash = calculationHash(snapshot, profile);
    AccountingPocRepository.PeriodState state = repository.periodState(period);
    boolean confirmed = state != null && hash.equals(state.confirmedCalculationHash());
    List<String> issues = new ArrayList<>();
    snapshot.issues().stream()
        .filter(i -> !"INFO".equals(i.severity()))
        .forEach(i -> issues.add(i.message()));
    if (!confirmed) issues.add("Month calculation is not confirmed or changed after confirmation.");
    if (blank(profile.nip())
        || blank(profile.fullName())
        || blank(profile.taxOfficeCode())
        || blank(profile.email())) {
      issues.add("MISSING_TAXPAYER_CONFIGURATION");
    }
    return new FilingResult(period, snapshot, profile, hash, confirmed, issues);
  }

  public void confirm(LocalDate period) {
    FilingResult result = filing(period);
    if (!result.issues().isEmpty()
        && !(result.issues().size() == 1
            && result.issues().getFirst().startsWith("Month calculation"))) {
      throw new IllegalStateException(
          "Cannot confirm month: " + String.join("; ", result.issues()));
    }
    repository.confirm(period, result.calculationHash(), Instant.now());
  }

  public byte[] jpk(LocalDate period) {
    FilingResult result = filing(period);
    if (!result.ready()) throw new IllegalStateException(String.join("; ", result.issues()));
    return jpkGenerator.generate(result);
  }

  public List<AccountingPaymentInstruction> paymentInstructions(LocalDate period) {
    FilingResult result = filing(period);
    if (!result.ready()) throw new IllegalStateException(String.join("; ", result.issues()));
    AccountingMonthSnapshot s = result.snapshot();
    AccountingProfile p = result.profile();
    List<AccountingPaymentInstruction> output = new ArrayList<>();
    add(
        output,
        period,
        "VAT",
        s.vat().calculatedVat(),
        p.vatPaymentAccount(),
        "VAT-7",
        s.obligations());
    add(
        output,
        period,
        "RYCZALT",
        s.ryczalt().calculatedTax(),
        p.ryczaltPaymentAccount(),
        "RYCZALT",
        s.obligations());
    add(output, period, "ZUS", s.zus().totalZus(), p.zusPaymentAccount(), "ZUS", s.obligations());
    return output;
  }

  private void add(
      List<AccountingPaymentInstruction> out,
      LocalDate period,
      String type,
      BigDecimal amount,
      String account,
      String title,
      List<AccountingMonthSnapshot.ObligationRow> obligations) {
    if (amount == null || amount.signum() <= 0) return;
    if (blank(account)) throw new IllegalStateException("MISSING_PAYMENT_CONFIGURATION: " + type);
    AccountingMonthSnapshot.ObligationRow existing =
        obligations.stream().filter(o -> type.equals(o.obligationType())).findFirst().orElse(null);
    BigDecimal paid =
        existing == null || existing.paidAmount() == null ? BigDecimal.ZERO : existing.paidAmount();
    String status =
        paid.signum() == 0
            ? "NOT_DUE"
            : paid.compareTo(amount) < 0
                ? "PARTIAL"
                : paid.compareTo(amount) > 0 ? "OVERPAID" : "PAID";
    out.add(
        new AccountingPaymentInstruction(
            type,
            amount,
            dueDatePolicy.dueDate(period, type),
            type.equals("ZUS") ? "ZUS" : "TAX_OFFICE",
            account,
            title + " " + period,
            period,
            status,
            paid));
  }

  private String calculationHash(AccountingMonthSnapshot s, AccountingProfile p) {
    String value =
        s.period()
            + "|"
            + s.invoices()
            + "|"
            + s.expenses()
            + "|"
            + s.vat()
            + "|"
            + s.ryczalt()
            + "|"
            + s.zus()
            + "|"
            + p.hasUop();
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte b : digest) result.append("%02x".formatted(b));
      return result.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  public record FilingResult(
      LocalDate period,
      AccountingMonthSnapshot snapshot,
      AccountingProfile profile,
      String calculationHash,
      boolean confirmed,
      List<String> issues) {
    public boolean ready() {
      return confirmed && issues.isEmpty();
    }
  }
}
