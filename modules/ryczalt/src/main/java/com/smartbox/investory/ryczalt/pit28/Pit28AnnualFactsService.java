package com.smartbox.investory.ryczalt.pit28;

import com.smartbox.investory.ryczalt.application.RyczaltAccountingApi;
import com.smartbox.investory.ryczalt.application.query.RyczaltInvoiceReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPaymentHistoryReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates authoritative persisted Ryczalt facts for the narrow PIT-28 preview. */
@Service
public class Pit28AnnualFactsService {
  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("PLN", "EUR", "USD");

  private final RyczaltAccountingApi accounting;

  public Pit28AnnualFactsService(RyczaltAccountingApi accounting) {
    this.accounting = accounting;
  }

  @Transactional(readOnly = true)
  public Result collect(long profileId, int year) {
    List<Pit28Issue> issues = new ArrayList<>();
    Map<String, BigDecimal> byCurrency = new HashMap<>();
    Set<BigDecimal> rates = new TreeSet<>();
    BigDecimal revenue = BigDecimal.ZERO;
    BigDecimal socialPaid = BigDecimal.ZERO;
    BigDecimal socialDeductible = BigDecimal.ZERO;
    BigDecimal healthPaid = BigDecimal.ZERO;
    BigDecimal healthDeductible = BigDecimal.ZERO;
    boolean hasZusFacts = false;
    Map<YearMonth, Pit28MonthlyZusFacts> zusByMonth = new HashMap<>();
    List<Monthly> monthly = new ArrayList<>();
    Set<YearMonth> months =
        accounting.periods(profileId).stream()
            .map(value -> value.month())
            .filter(value -> value.getYear() == year)
            .collect(Collectors.toCollection(TreeSet::new));

    for (YearMonth month : months) {
      RyczaltPeriodReadModel period = accounting.period(profileId, month);
      if (!"COMPLETE".equals(period.completeness().status())) {
        issues.add(
            issue("STALE_MONTH", "Accounting period is not complete: " + month, month.toString()));
      }
      if (period.calculations().stream().anyMatch(value -> "ZUS".equals(value.type()))) {
        hasZusFacts = true;
        zusByMonth.put(month, accounting.zusFacts(profileId, month));
      }
      socialDeductible = socialDeductible.add(value(period.audit().socialDeduction()));
      healthDeductible = healthDeductible.add(value(period.audit().healthDeduction()));
      List<RyczaltInvoiceReadModel> invoices = accounting.invoices(profileId, month);
      for (RyczaltInvoiceReadModel invoice : invoices) {
        if (invoice.direction() != InvoiceDirection.INCOME
            || invoice.accountingDate() == null
            || invoice.accountingDate().getYear() != year) continue;
        String reference = invoice.reference();
        if (invoice.approvalStatus() != ApprovalStatus.APPROVED) {
          issues.add(issue("UNAPPROVED_INVOICE", "Invoice requires approval", reference));
        }
        if (invoice.ryczaltRate() == null) {
          issues.add(issue("MISSING_RYCZALT_RATE", "Invoice has no Ryczalt rate", reference));
        } else {
          rates.add(invoice.ryczaltRate());
        }
        String currency = invoice.currency() == null ? null : invoice.currency().name();
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
          issues.add(issue("UNSUPPORTED_CURRENCY", "Invoice currency is not supported", reference));
          continue;
        }
        if (invoice.bookedNetPln() == null) {
          issues.add(
              issue(
                  "EUR".equals(currency) || "USD".equals(currency)
                      ? "MISSING_FX"
                      : "MISSING_BOOKED_PLN",
                  "Authoritative booked PLN amount is missing",
                  reference));
          continue;
        }
        revenue = revenue.add(invoice.bookedNetPln());
        byCurrency.merge(currency, invoice.bookedNetPln(), BigDecimal::add);
      }
      monthly.add(
          new Monthly(
              month,
              value(period.summary().revenue()),
              value(period.summary().ryczalt()),
              BigDecimal.ZERO,
              period.completeness().status()));
    }

    List<RyczaltPaymentHistoryReadModel> zusPayments =
        accounting.paymentHistory(profileId, YearMonth.of(year, 1), YearMonth.of(year, 12), "ZUS");
    for (RyczaltPaymentHistoryReadModel payment : zusPayments) {
      BigDecimal paid = value(payment.paidAmount());
      Pit28MonthlyZusFacts expected = zusByMonth.get(payment.period());
      if (expected == null) {
        issues.add(
            issue(
                "MISSING_ZUS_FACTS",
                "ZUS calculation facts are missing",
                payment.period().toString()));
      } else {
        BigDecimal social = paid.min(value(expected.socialContribution()));
        socialPaid = socialPaid.add(social);
        healthPaid =
            healthPaid.add(
                paid.subtract(social)
                    .min(value(expected.healthContribution()))
                    .max(BigDecimal.ZERO));
      }
    }
    if (!hasZusFacts && revenue.signum() > 0) {
      issues.add(issue("MISSING_ZUS_FACTS", "Annual ZUS calculation facts are missing", null));
    }
    List<RyczaltPaymentHistoryReadModel> ryczaltPayments =
        accounting.paymentHistory(
            profileId, YearMonth.of(year, 1), YearMonth.of(year, 12), "RYCZALT");
    BigDecimal paidTax =
        ryczaltPayments.stream()
            .map(RyczaltPaymentHistoryReadModel::paidAmount)
            .map(Pit28AnnualFactsService::value)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    Map<YearMonth, BigDecimal> paidByMonth =
        ryczaltPayments.stream()
            .collect(
                Collectors.groupingBy(
                    RyczaltPaymentHistoryReadModel::period,
                    Collectors.reducing(
                        BigDecimal.ZERO, value -> value(value.paidAmount()), BigDecimal::add)));
    monthly =
        monthly.stream()
            .map(
                value ->
                    new Monthly(
                        value.month(),
                        value.revenue(),
                        value.monthlyTax(),
                        value(value.paidTax())
                            .add(paidByMonth.getOrDefault(value.month(), BigDecimal.ZERO)),
                        value.status()))
            .toList();
    boolean unsupported =
        rates.size() > 1
            || issues.stream().anyMatch(value -> "UNSUPPORTED_CURRENCY".equals(value.code()));
    if (rates.isEmpty() && revenue.signum() > 0) {
      issues.add(issue("MISSING_RYCZALT_RATE", "No annual Ryczalt rate is configured", null));
    }
    Pit28ReadinessStatus status =
        unsupported
            ? Pit28ReadinessStatus.UNSUPPORTED
            : issues.isEmpty() ? Pit28ReadinessStatus.READY : Pit28ReadinessStatus.BLOCKED;
    if (rates.size() > 1) {
      issues.add(
          issue("MULTIPLE_RYCZALT_RATES", "PIT-28 v1 supports one annual Ryczalt rate", null));
    }
    BigDecimal rate = rates.size() == 1 ? rates.iterator().next() : BigDecimal.ZERO;
    Pit28AnnualFacts facts =
        new Pit28AnnualFacts(
            year,
            revenue,
            byCurrency,
            socialPaid,
            socialDeductible,
            healthPaid,
            healthDeductible,
            rate,
            paidTax);
    return new Result(facts, new Pit28Readiness(status, issues), monthly);
  }

  private static Pit28Issue issue(String code, String message, String reference) {
    return new Pit28Issue(code, message, reference);
  }

  private static BigDecimal value(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  public record Result(Pit28AnnualFacts facts, Pit28Readiness readiness, List<Monthly> monthly) {
    public Result {
      monthly = List.copyOf(monthly);
    }
  }

  public record Monthly(
      YearMonth month,
      BigDecimal revenue,
      BigDecimal monthlyTax,
      BigDecimal paidTax,
      String status) {}
}
