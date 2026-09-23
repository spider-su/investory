package com.smartbox.investory.ryczalt.checker;

import java.math.BigDecimal;

public record PaymentAllocation(String transactionId, BigDecimal amount) {}
