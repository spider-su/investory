package com.smartbox.investory.ryczalt.application.query;

import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import com.smartbox.investory.ryczalt.domain.ObligationType;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RyczaltObligationReadModel(
    long id,
    ObligationType type,
    BigDecimal expectedAmount,
    BigDecimal paidAmount,
    BigDecimal outstandingAmount,
    CurrencyType currency,
    LocalDate dueDate,
    ObligationStatus status,
    boolean manuallyPaid,
    LocalDate manualPaidDate) {}
