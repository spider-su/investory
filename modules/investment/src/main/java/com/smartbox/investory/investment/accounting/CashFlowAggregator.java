package com.smartbox.investory.investment.accounting;

import com.smartbox.investory.investment.infrastructure.persistence.CashOperationEntity;
import com.smartbox.investory.investment.market.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Aggregates {@link CashOperationEntity} rows into base-currency totals (deposits, withdrawals, interest,
 * dividends and dividend tax) plus a per-currency dividends breakdown.
 *
 * <p>Extracted from {@code PortfolioService.calculateTotalProfitLoss()} so the cash-side accounting
 * can be unit-tested in isolation from positions / tax / FX board logic.
 */
@Component
@RequiredArgsConstructor
public class CashFlowAggregator {

  private static final int MONEY_SCALE = 8;

  private final CurrencyRateService currencyRateService;

  /**
   * Result of the aggregation in {@code baseCurrency} (except {@link #dividendsByCurrency()} which
   * is kept in native currencies for the dashboard's per-currency board).
   */
  public record CashFlowSummary(
      BigDecimal deposits,
      BigDecimal withdrawals,
      BigDecimal interest,
      BigDecimal dividends,
      BigDecimal dividendTax,
      Map<CurrencyType, BigDecimal> dividendsByCurrency) {

    public BigDecimal netDeposits() {
      return scale(deposits.add(withdrawals));
    }
  }

  public CashFlowSummary aggregate(List<CashOperationEntity> operations, CurrencyType baseCurrency) {
    Map<CurrencyType, List<CashOperationEntity>> byCurrency =
        operations.stream().collect(Collectors.groupingBy(CashOperationEntity::getCurrency));

    BigDecimal deposits = BigDecimal.ZERO;
    BigDecimal withdrawals = BigDecimal.ZERO;
    BigDecimal interest = BigDecimal.ZERO;
    BigDecimal dividends = BigDecimal.ZERO;
    BigDecimal dividendTax = BigDecimal.ZERO;
    Map<CurrencyType, BigDecimal> dividendsByCurrency = new HashMap<>();

    for (Map.Entry<CurrencyType, List<CashOperationEntity>> entry : byCurrency.entrySet()) {
      CurrencyType currency = entry.getKey();
      List<CashOperationEntity> positions = entry.getValue();

      BigDecimal grossDividends = BigDecimal.ZERO;
      BigDecimal withholdingTax = BigDecimal.ZERO;

      for (CashOperationEntity op : positions) {
        if (op.getType() == null) {
          continue;
        }
        LocalDate rateDate = op.getDate() != null ? op.getDate().toLocalDate() : LocalDate.now();
        BigDecimal amount = nz(op.getAmountValue());
        BigDecimal base =
            currencyRateService.convertToBaseCurrency(amount, baseCurrency, currency, rateDate);
        switch (op.getType()) {
          case DIVIDEND:
            grossDividends = grossDividends.add(amount);
            dividends = dividends.add(base);
            break;
          case WITHHOLDING_TAX:
            BigDecimal signedTax = base.signum() > 0 ? base.negate() : base;
            withholdingTax = withholdingTax.add(signedTax);
            dividends = dividends.add(signedTax);
            dividendTax = dividendTax.add(signedTax);
            break;
          case DEPOSIT:
            if (isExternalFunding(op)) {
              deposits = deposits.add(base);
            }
            break;
          case WITHDRAWAL:
            if (isExternalFunding(op)) {
              withdrawals = withdrawals.add(base);
            }
            break;
          case FREE_FUNDS_INTEREST:
          case FREE_FUNDS_INTEREST_TAX:
            interest = interest.add(base);
            break;
          default:
            break;
        }
      }

      dividendsByCurrency.merge(currency, grossDividends.add(withholdingTax), BigDecimal::add);
    }

    return new CashFlowSummary(
        scale(deposits),
        scale(withdrawals),
        scale(interest),
        scale(dividends),
        scale(dividendTax),
        dividendsByCurrency.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> scale(entry.getValue()))));
  }

  /**
   * Whether a deposit/withdrawal represents real external cash funding rather than an internal
   * sub-account transfer or a currency conversion (which the broker also books as Deposit /
   * Withdraw rows but are not actual income/outflow for the portfolio).
   */
  static boolean isExternalFunding(CashOperationEntity operation) {
    String comment = operation.getComment();
    if (comment == null) {
      return true;
    }
    String lower = comment.toLowerCase();
    // Exclude internal movements only: XTB sub-account transfers ("Transfer from X to Y")
    // and FX conversions. IBKR real funding reads "Electronic Fund Transfer" and must count.
    return !(lower.contains("currency conversion")
        || lower.contains("transfer in operation")
        || lower.contains("transfer out operation")
        || lower.contains("transfer from")
        || lower.contains("transfer to"));
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private static BigDecimal scale(BigDecimal value) {
    return nz(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }
}
