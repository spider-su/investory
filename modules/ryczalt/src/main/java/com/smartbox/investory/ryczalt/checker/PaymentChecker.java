package com.smartbox.investory.ryczalt.checker;

import com.smartbox.investory.ryczalt.domain.Obligation;
import com.smartbox.investory.ryczalt.domain.Transaction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure, deterministic payment checker. It proposes evidence; it does not persist matches. */
public final class PaymentChecker {
  public PaymentCheckResult check(Obligation obligation, List<Transaction> transactions) {
    return check(obligation, transactions, PaymentAccountRules.empty());
  }

  public PaymentCheckResult check(
      Obligation obligation, List<Transaction> transactions, PaymentAccountRules accountRules) {
    List<Transaction> currencyCandidates =
        transactions.stream()
            .filter(transaction -> transaction.currency().equals(obligation.currency()))
            .filter(transaction -> transaction.amount().signum() != 0)
            .sorted(Comparator.comparing(Transaction::date).thenComparing(Transaction::reference))
            .toList();
    if (currencyCandidates.isEmpty()) {
      boolean otherCurrency =
          transactions.stream().anyMatch(transaction -> transaction.amount().signum() != 0);
      return result(
          obligation,
          PaymentCheckStatus.NOT_FOUND,
          List.of(),
          otherCurrency
              ? List.of(
                  new PaymentIssue(PaymentIssue.Code.WRONG_CURRENCY, "no compatible currency"))
              : List.of());
    }

    List<Transaction> evidenced =
        currencyCandidates.stream()
            .filter(transaction -> hasPaymentEvidence(obligation, transaction, accountRules))
            .toList();
    List<Transaction> candidates = evidenced;
    if (candidates.isEmpty()) {
      List<Transaction> exactWithoutType =
          currencyCandidates.stream()
              .filter(
                  transaction ->
                      PaymentReconciliationPolicy.sameObligationAmount(
                              obligation.amount(), transaction.amount().abs())
                          && allowsExactAmountFallback(obligation, transaction, accountRules))
              .toList();
      if (exactWithoutType.size() > 1) {
        return result(
            obligation,
            PaymentCheckStatus.AMBIGUOUS,
            List.of(),
            List.of(
                new PaymentIssue(
                    PaymentIssue.Code.AMBIGUOUS_CANDIDATES, "multiple exact candidates")));
      }
      if (exactWithoutType.size() == 1) {
        return result(
            obligation,
            status(obligation, exactWithoutType.getFirst().date()),
            allocations(exactWithoutType),
            List.of());
      }
    }
    List<Transaction> exact =
        candidates.stream()
            .filter(
                transaction ->
                    PaymentReconciliationPolicy.sameObligationAmount(
                        obligation.amount(), transaction.amount().abs()))
            .toList();
    if (exact.size() > 1) {
      return result(
          obligation,
          PaymentCheckStatus.AMBIGUOUS,
          List.of(),
          List.of(
              new PaymentIssue(
                  PaymentIssue.Code.AMBIGUOUS_CANDIDATES, "multiple exact candidates")));
    }
    if (exact.size() == 1)
      return result(
          obligation, status(obligation, exact.getFirst().date()), allocations(exact), List.of());

    if (candidates.isEmpty()) {
      return result(obligation, PaymentCheckStatus.NOT_FOUND, List.of(), List.of());
    }
    List<PaymentAllocation> allocations = allocate(candidates, obligation.amount());
    BigDecimal matched =
        allocations.stream()
            .map(PaymentAllocation::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (matched.signum() == 0)
      return result(obligation, PaymentCheckStatus.NOT_FOUND, List.of(), List.of());
    PaymentCheckStatus status =
        matched.compareTo(obligation.amount()) > 0
            ? PaymentCheckStatus.OVERPAID
            : matched.compareTo(obligation.amount()) < 0
                ? PaymentCheckStatus.PARTIALLY_PAID
                : status(obligation, latestDate(candidates, allocations));
    return result(obligation, status, allocations, List.of());
  }

  private List<PaymentAllocation> allocate(List<Transaction> candidates, BigDecimal expected) {
    List<PaymentAllocation> result = new ArrayList<>();
    BigDecimal remaining = expected;
    for (Transaction candidate : candidates) {
      if (remaining.signum() <= 0) break;
      BigDecimal allocation = candidate.amount().abs();
      result.add(new PaymentAllocation(candidate.reference(), allocation));
      remaining = remaining.subtract(candidate.amount().abs());
    }
    return result;
  }

  private PaymentCheckStatus status(Obligation obligation, java.time.LocalDate paymentDate) {
    if (obligation.dueDate() != null && paymentDate.isAfter(obligation.dueDate()))
      return PaymentCheckStatus.PAID_LATE;
    return PaymentCheckStatus.PAID;
  }

  private java.time.LocalDate latestDate(
      List<Transaction> candidates, List<PaymentAllocation> allocations) {
    return candidates.stream()
        .filter(
            transaction ->
                allocations.stream()
                    .anyMatch(a -> a.transactionReference().equals(transaction.reference())))
        .map(Transaction::date)
        .max(Comparator.naturalOrder())
        .orElseThrow();
  }

  private boolean mentionsType(Obligation obligation, Transaction transaction) {
    String text =
        ((transaction.reference() == null ? "" : transaction.reference())
                + " "
                + (transaction.counterparty() == null ? "" : transaction.counterparty())
                + " "
                + (transaction.description() == null ? "" : transaction.description()))
            .toUpperCase(Locale.ROOT);
    return switch (obligation.type()) {
      case ZUS -> text.contains("ZUS");
      case VAT -> text.contains("VAT") || text.contains("JPK");
      // Polish tax-office transfers commonly identify the ryczałt advance as
      // PPE (the bank format uses SFP/PPE), without spelling out PIT or RYCZALT.
      case RYCZALT ->
          text.contains("RYCZ")
              || text.contains("PIT")
              || text.contains("TAX")
              || text.contains("PPE");
    };
  }

  private boolean hasPaymentEvidence(
      Obligation obligation, Transaction transaction, PaymentAccountRules accountRules) {
    String expectedAccount = accountRules.accountFor(obligation.type());
    String transactionAccount = PaymentAccountRules.normalize(transaction.counterpartyAccount());
    if (!transactionAccount.isBlank() && expectedAccount != null) {
      if (!transactionAccount.equals(expectedAccount)) return false;
      if (accountRules.isUniqueFor(obligation.type(), transactionAccount)) return true;
    }
    return mentionsType(obligation, transaction);
  }

  private boolean allowsExactAmountFallback(
      Obligation obligation, Transaction transaction, PaymentAccountRules accountRules) {
    String expectedAccount = accountRules.accountFor(obligation.type());
    String transactionAccount = PaymentAccountRules.normalize(transaction.counterpartyAccount());
    return expectedAccount == null || transactionAccount.isBlank();
  }

  private PaymentCheckResult result(
      Obligation obligation,
      PaymentCheckStatus status,
      List<PaymentAllocation> allocations,
      List<PaymentIssue> issues) {
    BigDecimal matched =
        allocations.stream()
            .map(PaymentAllocation::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new PaymentCheckResult(status, obligation.amount(), matched, allocations, issues);
  }

  private List<PaymentAllocation> allocations(List<Transaction> transactions) {
    return transactions.stream()
        .map(transaction -> new PaymentAllocation(transaction.reference(), transaction.amount()))
        .toList();
  }
}
