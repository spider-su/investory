package com.smartbox.investory.investment.api.reporting.model;

import java.math.BigDecimal;

/** Funding of the displayed accounts; gross amounts are unavailable if source flows are missing. */
public record AccountFundingView(
    BigDecimal netDeposits, BigDecimal deposits, BigDecimal withdrawals) {}
