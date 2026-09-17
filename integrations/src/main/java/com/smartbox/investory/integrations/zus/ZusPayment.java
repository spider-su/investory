package com.smartbox.investory.integrations.zus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ZusPayment(
    String externalId,
    LocalDate paymentDate,
    BigDecimal amount,
    String currency,
    String title,
    ZusPaymentStatus status) {}
