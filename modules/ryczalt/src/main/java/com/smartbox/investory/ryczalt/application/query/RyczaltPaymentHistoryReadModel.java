package com.smartbox.investory.ryczalt.application.query;

import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import com.smartbox.investory.ryczalt.domain.ObligationType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record RyczaltPaymentHistoryReadModel(
    ObligationType type,
    YearMonth period,
    BigDecimal expectedAmount,
    BigDecimal paidAmount,
    BigDecimal outstandingAmount,
    LocalDate dueDate,
    LocalDate paymentDate,
    ObligationStatus status) {}
