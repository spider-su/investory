package com.smartbox.investory.longterm.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RentalContract(Long id, LocalDate startDate, LocalDate endDate, List<Term> terms) {
  public RentalContract { terms = terms == null ? List.of() : List.copyOf(terms); }
  public record Term(CashFlowType type, BigDecimal amount, Frequency frequency, boolean paidByTenant) {}
}
