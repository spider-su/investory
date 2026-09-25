package com.smartbox.investory.ui.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Builds display-only values for the native Ryczalt accounting page. */
@Component
final class RyczaltAccountingPageViewAssembler {
  static RyczaltWebAccountingClient.Period emptyPeriod(YearMonth month) {
    return new RyczaltWebAccountingClient.Period(
        month,
        "OPEN",
        List.of(),
        new RyczaltWebAccountingClient.Summary(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
        new RyczaltWebAccountingClient.Audit(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO),
        new RyczaltWebAccountingClient.Documents(0, 0),
        new RyczaltWebAccountingClient.Settlement(
            0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true),
        new RyczaltWebAccountingClient.Reconciliation(0, 0, 0, 0),
        new RyczaltWebAccountingClient.Completeness("INCOMPLETE", 0),
        List.of());
  }

  PageView assemble(
      YearMonth selected,
      List<RyczaltWebAccountingClient.Month> periods,
      RyczaltWebAccountingClient.Period period,
      List<RyczaltWebAccountingClient.Invoice> invoices,
      List<RyczaltWebAccountingClient.Transaction> currentMonthTransactions,
      List<RyczaltWebAccountingClient.Transaction> nextMonthTransactions,
      List<RyczaltWebAccountingClient.Obligation> obligations,
      List<RyczaltWebAccountingClient.ReferenceObligation> referenceObligations,
      List<RyczaltWebAccountingClient.Issue> issues,
      List<RyczaltWebAccountingClient.Counterparty> counterparties,
      boolean canWrite,
      LocalDate today) {
    var selectedMonthInPeriods = periods.stream().anyMatch(value -> value.month().equals(selected));
    var transactions =
        selectedMonthInPeriods
            ? bankTransactionsForDisplay(currentMonthTransactions, nextMonthTransactions)
            : List.<RyczaltWebAccountingClient.Transaction>of();
    return new PageView(
        selected,
        selected.minusMonths(1),
        selected.plusMonths(1),
        selectedMonthInPeriods,
        periods,
        period,
        invoices,
        invoices.stream().filter(item -> "INCOME".equals(item.direction())).toList(),
        invoices.stream().filter(item -> "COST".equals(item.direction())).toList(),
        transactions,
        bankTransactionRows(counterparties, transactions),
        obligations,
        issues,
        today,
        canWrite,
        whole(period.settlement().totalOutstanding()),
        whole(period.settlement().totalPaid()),
        obligations.stream()
            .filter(item -> item.outstanding() != null && item.outstanding().signum() > 0)
            .map(RyczaltWebAccountingClient.Obligation::dueDate)
            .filter(java.util.Objects::nonNull)
            .min(java.util.Comparator.naturalOrder())
            .orElse(null),
        taxCards(period, obligations, referenceObligations));
  }

  private static List<TaxCard> taxCards(
      RyczaltWebAccountingClient.Period period,
      List<RyczaltWebAccountingClient.Obligation> obligations,
      List<RyczaltWebAccountingClient.ReferenceObligation> referenceObligations) {
    return List.of(
        taxCard("Ryczalt", period.summary().ryczalt(), obligations, referenceObligations),
        taxCard("VAT", period.summary().vat(), obligations, referenceObligations),
        taxCard("ZUS", period.summary().zus(), obligations, referenceObligations));
  }

  private static TaxCard taxCard(
      String type,
      BigDecimal calculated,
      List<RyczaltWebAccountingClient.Obligation> obligations,
      List<RyczaltWebAccountingClient.ReferenceObligation> referenceObligations) {
    var reference =
        referenceObligations.stream()
            .filter(item -> type.equalsIgnoreCase(item.type()))
            .map(RyczaltWebAccountingClient.ReferenceObligation::expected)
            .filter(value -> value != null)
            .reduce(BigDecimal::add)
            .orElse(null);
    var bank =
        obligations.stream()
            .filter(item -> type.equalsIgnoreCase(item.type()))
            .map(RyczaltWebAccountingClient.Obligation::paid)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var hasObligation = obligations.stream().anyMatch(item -> type.equalsIgnoreCase(item.type()));
    var outstanding =
        obligations.stream()
            .filter(item -> type.equalsIgnoreCase(item.type()))
            .map(RyczaltWebAccountingClient.Obligation::outstanding)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var paid = hasObligation && outstanding.signum() <= 0;
    return new TaxCard(
        type,
        whole(calculated),
        whole(reference),
        difference(calculated, reference),
        whole(bank),
        difference(calculated, bank),
        paid ? "✓ Paid" : "○ Unpaid",
        paid ? "is-paid" : "is-unpaid");
  }

  static List<BankTransactionRow> bankTransactionRows(
      List<RyczaltWebAccountingClient.Counterparty> counterparties,
      List<RyczaltWebAccountingClient.Transaction> transactions) {
    Map<String, String> labels = new HashMap<>();
    counterparties.forEach(
        counterparty -> {
          String label =
              counterparty.alias() == null || counterparty.alias().isBlank()
                  ? counterparty.displayName()
                  : counterparty.alias();
          putCounterpartyLabel(labels, counterparty.legalName(), label);
          putCounterpartyLabel(labels, counterparty.alias(), label);
          putCounterpartyLabel(labels, counterparty.displayName(), label);
        });
    return transactions.stream()
        .map(
            transaction -> {
              String counterparty = transaction.counterparty();
              String label = labels.getOrDefault(normalizeCounterparty(counterparty), counterparty);
              BigDecimal amount =
                  transaction.amount() == null ? BigDecimal.ZERO : transaction.amount();
              BigDecimal matched =
                  transaction.matchedAmount() == null
                      ? BigDecimal.ZERO
                      : transaction.matchedAmount();
              String status =
                  matched.signum() == 0
                      ? "Unmatched"
                      : matched.compareTo(amount.abs()) >= 0 ? "Matched" : "Partially matched";
              return new BankTransactionRow(
                  label == null || label.isBlank() ? "—" : label,
                  transaction.bookingDate(),
                  whole(amount),
                  status,
                  transaction.description());
            })
        .toList();
  }

  static List<RyczaltWebAccountingClient.Transaction> bankTransactionsForDisplay(
      List<RyczaltWebAccountingClient.Transaction> currentMonth,
      List<RyczaltWebAccountingClient.Transaction> nextMonth) {
    return Stream.concat(
            currentMonth.stream().filter(transaction -> signum(transaction.amount()) > 0),
            nextMonth.stream().filter(transaction -> signum(transaction.amount()) < 0))
        .sorted(
            java.util.Comparator.comparing(
                    RyczaltWebAccountingClient.Transaction::bookingDate,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparingLong(RyczaltWebAccountingClient.Transaction::id))
        .toList();
  }

  private static int signum(BigDecimal value) {
    return value == null ? 0 : value.signum();
  }

  private static void putCounterpartyLabel(Map<String, String> labels, String name, String label) {
    String key = normalizeCounterparty(name);
    if (!key.isEmpty() && label != null && !label.isBlank()) labels.putIfAbsent(key, label);
  }

  private static String normalizeCounterparty(String name) {
    if (name == null || name.isBlank()) return "";
    return Normalizer.normalize(name, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replaceAll("[^\\p{Alnum}]", "")
        .toUpperCase(Locale.ROOT);
  }

  private static String whole(BigDecimal value) {
    return value == null ? "—" : value.setScale(0, RoundingMode.HALF_UP).toPlainString();
  }

  private static String difference(BigDecimal calculated, BigDecimal comparison) {
    if (calculated == null || comparison == null) return null;
    var difference = calculated.subtract(comparison).setScale(0, RoundingMode.HALF_UP);
    return difference.signum() == 0
        ? null
        : (difference.signum() > 0 ? "+" : "") + difference.toPlainString();
  }

  record PageView(
      YearMonth selectedMonth,
      YearMonth previousMonth,
      YearMonth nextMonth,
      boolean selectedMonthInPeriods,
      List<RyczaltWebAccountingClient.Month> periods,
      RyczaltWebAccountingClient.Period period,
      List<RyczaltWebAccountingClient.Invoice> invoices,
      List<RyczaltWebAccountingClient.Invoice> incomeInvoices,
      List<RyczaltWebAccountingClient.Invoice> costInvoices,
      List<RyczaltWebAccountingClient.Transaction> transactions,
      List<BankTransactionRow> bankTransactionRows,
      List<RyczaltWebAccountingClient.Obligation> obligations,
      List<RyczaltWebAccountingClient.Issue> issues,
      LocalDate today,
      boolean canWrite,
      String toPayAmountDisplay,
      String paidAmountDisplay,
      LocalDate nextDueDate,
      List<TaxCard> taxCards) {}

  record BankTransactionRow(
      String counterparty,
      LocalDate bookingDate,
      String amount,
      String status,
      String description) {}

  record TaxCard(
      String type,
      String calculated,
      String reference,
      String referenceDiff,
      String bank,
      String bankDiff,
      String status,
      String statusClass) {}
}
