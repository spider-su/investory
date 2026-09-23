package com.smartbox.investory.ryczalt.calculation.application;

import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculationResult;
import com.smartbox.investory.ryczalt.calculation.vat.VatCalculationResult;
import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculationResult;
import java.math.BigDecimal;
import java.time.YearMonth;

public record NativeMonthCalculationResult(
    YearMonth month,
    BigDecimal ryczalt,
    BigDecimal vat,
    BigDecimal zus,
    RyczaltCalculationResult ryczaltResult,
    VatCalculationResult vatResult,
    ZusCalculationResult zusResult) {}
