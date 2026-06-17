package com.example.demo.services;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.services.currency.CurrencyRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregates {@link CashOperation} rows into base-currency totals (deposits, withdrawals,
 * interest, dividends and dividend tax) plus a per-currency dividends breakdown.
 *
 * <p>Extracted from {@code PortfolioService.calculateTotalProfitLoss()} so the cash-side
 * accounting can be unit-tested in isolation from positions / tax / FX board logic.
 */
@Component
@RequiredArgsConstructor
public class CashFlowAggregator {

    private final CurrencyRateService currencyRateService;

    /**
     * Result of the aggregation in {@code baseCurrency} (except {@link #dividendsByCurrency()}
     * which is kept in native currencies for the dashboard's per-currency board).
     */
    public record CashFlowSummary(
            double deposits,
            double withdrawals,
            double interest,
            double dividends,
            double dividendTax,
            Map<CurrencyType, Double> dividendsByCurrency) {

        public double netDeposits() {
            return deposits + withdrawals;
        }
    }

    public CashFlowSummary aggregate(List<CashOperation> operations, CurrencyType baseCurrency) {
        Map<CurrencyType, List<CashOperation>> byCurrency = operations.stream()
                .collect(Collectors.groupingBy(CashOperation::getCurrency));

        double deposits = 0.0;
        double withdrawals = 0.0;
        double interest = 0.0;
        double dividends = 0.0;
        double dividendTax = 0.0;
        Map<CurrencyType, Double> dividendsByCurrency = new HashMap<>();

        for (Map.Entry<CurrencyType, List<CashOperation>> entry : byCurrency.entrySet()) {
            CurrencyType currency = entry.getKey();
            List<CashOperation> positions = entry.getValue();

            double grossDividends = positions.stream()
                    .filter(op -> op.getType() == CashOperationType.DIVIDEND)
                    .mapToDouble(op -> nz(op.getAmount()))
                    .sum();
            // Dividend withholding tax (already deducted at source); amounts are negative.
            double withholdingTax = positions.stream()
                    .filter(op -> op.getType() == CashOperationType.WITHHOLDING_TAX)
                    .mapToDouble(op -> nz(op.getAmount()))
                    .sum();
            double netDividends = grossDividends + withholdingTax;
            dividendsByCurrency.merge(currency, netDividends, Double::sum);
            dividends += currencyRateService.convertToBaseCurrency(netDividends, baseCurrency, currency);
            dividendTax += currencyRateService.convertToBaseCurrency(withholdingTax, baseCurrency, currency);

            for (CashOperation op : positions) {
                if (op.getType() == null) {
                    continue;
                }
                double base = currencyRateService.convertToBaseCurrency(nz(op.getAmount()), baseCurrency, currency);
                switch (op.getType()) {
                    case DEPOSIT:
                        if (isExternalFunding(op)) {
                            deposits += base;
                        }
                        break;
                    case WITHDRAWAL:
                        if (isExternalFunding(op)) {
                            withdrawals += base;
                        }
                        break;
                    case FREE_FUNDS_INTEREST:
                    case FREE_FUNDS_INTEREST_TAX:
                        interest += base;
                        break;
                    default:
                        break;
                }
            }
        }

        return new CashFlowSummary(deposits, withdrawals, interest, dividends, dividendTax, dividendsByCurrency);
    }

    /**
     * Whether a deposit/withdrawal represents real external cash funding rather than an internal
     * sub-account transfer or a currency conversion (which the broker also books as Deposit /
     * Withdraw rows but are not actual income/outflow for the portfolio).
     */
    static boolean isExternalFunding(CashOperation operation) {
        String comment = operation.getComment();
        if (comment == null) {
            return true;
        }
        String lower = comment.toLowerCase();
        // Exclude internal movements only: XTB sub-account transfers ("Transfer from X to Y")
        // and FX conversions. IBKR real funding reads "Electronic Fund Transfer" and must count.
        return !(lower.contains("currency conversion")
                || lower.contains("transfer from")
                || lower.contains("transfer to"));
    }

    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }
}

