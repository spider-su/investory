package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import com.smartbox.investory.ryczalt.domain.ObligationType;
import java.time.LocalDate;
import java.time.YearMonth;

public record PaymentHistoryResponse(
    ObligationType type,
    YearMonth period,
    String expectedAmount,
    String paidAmount,
    String outstandingAmount,
    LocalDate dueDate,
    LocalDate paymentDate,
    ObligationStatus status) {}
