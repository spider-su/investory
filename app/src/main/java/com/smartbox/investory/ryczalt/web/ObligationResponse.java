package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import com.smartbox.investory.ryczalt.domain.ObligationType;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;

public record ObligationResponse(
    long id,
    ObligationType type,
    String expectedAmount,
    String paidAmount,
    String outstandingAmount,
    CurrencyType currency,
    LocalDate dueDate,
    ObligationStatus status,
    boolean manuallyPaid,
    LocalDate manualPaidDate) {}
