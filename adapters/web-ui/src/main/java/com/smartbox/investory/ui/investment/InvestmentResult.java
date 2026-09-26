package com.smartbox.investory.ui.investment;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;

/** Page-facing current-year investment result. */
public record InvestmentResult(boolean available, BigDecimal amount, CurrencyType currency) {}
