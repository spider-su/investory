package com.smartbox.investory.ryczalt.settlement;

import com.smartbox.investory.ryczalt.checker.PaymentAccountRules;
import com.smartbox.investory.ryczalt.checker.PaymentAllocation;
import com.smartbox.investory.ryczalt.checker.PaymentCheckResult;
import com.smartbox.investory.ryczalt.checker.PaymentCheckStatus;
import com.smartbox.investory.ryczalt.checker.PaymentChecker;
import com.smartbox.investory.ryczalt.checker.PaymentMatchType;
import com.smartbox.investory.ryczalt.checker.TaxPaymentPeriodReference;
import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import com.smartbox.investory.ryczalt.domain.Transaction;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPaymentMatchEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPaymentMatchJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltTransactionEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltTransactionJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Currency;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Settlement orchestration. Calculators and canonical transactions remain separate. */
@Service
public class SettlementService {
  private final RyczaltPeriodJpaRepository periods;
  private final RyczaltObligationJpaRepository obligations;
  private final RyczaltTransactionJpaRepository transactions;
  private final RyczaltPaymentMatchJpaRepository matches;
  private final RyczaltPaymentAccountResolver paymentAccounts;
  private final PaymentChecker checker;
  private final BigDecimal paymentTolerance;

  public SettlementService(
      RyczaltPeriodJpaRepository periods,
      RyczaltObligationJpaRepository obligations,
      RyczaltTransactionJpaRepository transactions,
      RyczaltPaymentMatchJpaRepository matches,
      RyczaltPaymentAccountResolver paymentAccounts,
      @Value("${app.ryczalt.payment.tolerance-pln:0}") BigDecimal paymentTolerance) {
    this.periods = periods;
    this.obligations = obligations;
    this.transactions = transactions;
    this.matches = matches;
    this.paymentAccounts = paymentAccounts;
    this.checker = new PaymentChecker();
    this.paymentTolerance = paymentTolerance == null ? BigDecimal.ZERO : paymentTolerance;
  }

  @Transactional
  public List<PaymentCheckResult> settlePeriod(long profileId, YearMonth month) {
    RyczaltPeriodEntity period = findPeriod(profileId, month);
    List<RyczaltTransactionEntity> storedTransactions =
        transactions
            .findByProfileIdAndPeriodIdOrderByBookingDateAscIdAsc(profileId, period.id())
            .stream()
            .filter(transaction -> !transaction.isExcludedFromPaymentMatching())
            .toList();
    PaymentAccountRules accountRules = paymentAccounts.forProfile(profileId);
    return obligations.findByProfileIdAndPeriodIdOrderByTypeAsc(profileId, period.id()).stream()
        .map(
            obligation ->
                settleOne(profileId, period, obligation, storedTransactions, accountRules))
        .toList();
  }

  @Transactional
  public RyczaltPaymentMatchEntity matchPayment(
      long profileId, long obligationId, long transactionId, BigDecimal matchedAmount) {
    if (matchedAmount == null || matchedAmount.signum() <= 0) {
      throw new IllegalArgumentException("Matched amount must be positive");
    }
    RyczaltObligationEntity obligation = obligations.findById(obligationId).orElseThrow();
    RyczaltTransactionEntity transaction = transactions.findById(transactionId).orElseThrow();
    requireSameProfile(profileId, obligation.getProfileId(), transaction.getProfileId());
    if (transaction.isExcludedFromPaymentMatching()) {
      throw new IllegalArgumentException("Transaction is excluded from Ryczalt payment matching");
    }
    requireMutable(obligation.getPeriod());
    if (!obligation.getCurrency().equals(transaction.getCurrency())) {
      throw new IllegalArgumentException("Obligation and transaction currencies differ");
    }
    if (matches
        .findByProfileIdAndObligationIdAndTransactionId(profileId, obligationId, transactionId)
        .isPresent()) {
      throw new IllegalArgumentException("Payment pair is already matched");
    }
    BigDecimal transactionAllocated = matches.allocatedForTransaction(profileId, transactionId);
    if (transactionAllocated.add(matchedAmount).compareTo(transaction.getAmount().abs()) > 0) {
      throw new IllegalArgumentException("Transaction would be over-allocated");
    }
    BigDecimal obligationAllocated = matches.allocatedForObligation(profileId, obligationId);
    if (obligationAllocated.add(matchedAmount).compareTo(obligation.getAmount()) > 0) {
      throw new IllegalArgumentException("Obligation would be over-allocated");
    }
    RyczaltPaymentMatchEntity saved =
        matches.save(
            new RyczaltPaymentMatchEntity(
                profileId,
                obligation,
                transaction,
                matchedAmount,
                PaymentMatchType.MANUAL,
                Instant.now()));
    refreshStatus(profileId, obligation);
    return saved;
  }

  @Transactional
  public void unmatchPayment(long profileId, long paymentMatchId) {
    RyczaltPaymentMatchEntity match = matches.findById(paymentMatchId).orElseThrow();
    if (match.getProfileId() != profileId) throw new IllegalArgumentException("Profile mismatch");
    requireMutable(match.getObligation().getPeriod());
    RyczaltObligationEntity obligation = match.getObligation();
    matches.delete(match);
    refreshStatus(profileId, obligation);
  }

  private PaymentCheckResult settleOne(
      long profileId,
      RyczaltPeriodEntity period,
      RyczaltObligationEntity obligation,
      List<RyczaltTransactionEntity> storedTransactions,
      PaymentAccountRules accountRules) {
    if (period.getStatus().isFrozen())
      return new PaymentCheckResult(
          PaymentCheckStatus.NOT_FOUND,
          obligation.getAmount(),
          matches.allocatedForObligation(profileId, obligation.id()),
          List.of(),
          List.of());
    YearMonth obligationPeriod = YearMonth.of(period.getYear(), period.getMonth());
    List<Transaction> domainTransactions =
        storedTransactions.stream()
            .map(this::transaction)
            .filter(candidate -> TaxPaymentPeriodReference.matches(obligationPeriod, candidate))
            .toList();
    PaymentCheckResult result =
        checker.check(
            new com.smartbox.investory.ryczalt.domain.Obligation(
                obligation.getType(),
                obligation.getAmount(),
                Currency.getInstance(obligation.getCurrency().name()),
                obligation.getDueDate(),
                obligation.getStatus()),
            domainTransactions,
            accountRules);
    if (result.status() != PaymentCheckStatus.AMBIGUOUS
        && result.status() != PaymentCheckStatus.NOT_FOUND) {
      for (PaymentAllocation allocation : result.matchedTransactions()) {
        storedTransactions.stream()
            .filter(
                transaction ->
                    transaction(transaction).matchKey().equals(allocation.transactionId()))
            .findFirst()
            .ifPresent(
                transaction ->
                    createAutoMatch(profileId, obligation, transaction, allocation.amount()));
      }
    }
    refreshStatus(profileId, obligation);
    return result;
  }

  private void createAutoMatch(
      long profileId,
      RyczaltObligationEntity obligation,
      RyczaltTransactionEntity transaction,
      BigDecimal amount) {
    if (matches
        .findByProfileIdAndObligationIdAndTransactionId(
            profileId, obligation.id(), transaction.id())
        .isPresent()) return;
    BigDecimal available =
        transaction
            .getAmount()
            .abs()
            .subtract(matches.allocatedForTransaction(profileId, transaction.id()));
    BigDecimal accepted = amount.min(available);
    if (accepted.signum() <= 0) return;
    matches.save(
        new RyczaltPaymentMatchEntity(
            profileId, obligation, transaction, accepted, PaymentMatchType.AUTO, Instant.now()));
  }

  private void refreshStatus(long profileId, RyczaltObligationEntity obligation) {
    if (obligation.getStatus() == ObligationStatus.FROZEN) return;
    BigDecimal paid = matches.allocatedForObligation(profileId, obligation.id());
    BigDecimal difference = paid.subtract(obligation.getAmount());
    ObligationStatus status =
        paid.signum() == 0
            ? ObligationStatus.OPEN
            : difference.abs().compareTo(paymentTolerance) <= 0
                ? ObligationStatus.PAID
                : difference.signum() > 0
                    ? ObligationStatus.OVERPAID
                    : ObligationStatus.PARTIALLY_PAID;
    obligation.setStatus(status);
    obligations.save(obligation);
  }

  private Transaction transaction(RyczaltTransactionEntity entity) {
    return new Transaction(
        entity.id().toString(),
        entity.getReference(),
        entity.getBookingDate(),
        entity.getAmount(),
        Currency.getInstance(entity.getCurrency().name()),
        entity.getCounterparty(),
        entity.getCounterpartyAccount(),
        entity.getDescription());
  }

  private RyczaltPeriodEntity findPeriod(long profileId, YearMonth month) {
    return periods
        .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
        .orElseThrow(() -> new IllegalArgumentException("Period does not exist: " + month));
  }

  private void requireMutable(RyczaltPeriodEntity period) {
    if (period.getStatus().isFrozen())
      throw new IllegalStateException("Frozen period settlement is immutable");
  }

  private void requireSameProfile(long requested, long obligation, long transaction) {
    if (requested != obligation || requested != transaction)
      throw new IllegalArgumentException("Profile mismatch");
  }
}
