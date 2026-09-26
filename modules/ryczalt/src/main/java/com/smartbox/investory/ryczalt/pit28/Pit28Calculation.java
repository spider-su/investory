package com.smartbox.investory.ryczalt.pit28;

import java.math.BigDecimal;

public record Pit28Calculation(
    int year,
    BigDecimal grossRevenue,
    BigDecimal socialDeduction,
    BigDecimal healthDeduction,
    BigDecimal taxableRevenue,
    BigDecimal ryczaltRate,
    BigDecimal annualTax,
    BigDecimal taxPaid,
    BigDecimal amountDue,
    BigDecimal overpayment) {}
