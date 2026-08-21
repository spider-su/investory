package com.smartbox.investory.longterm.api.model;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RentalContractModel(Long id, LocalDate startDate, LocalDate endDate, List<Term> terms) {
  public RentalContractModel { terms = terms == null ? List.of() : List.copyOf(terms); }
  public record Term(CashFlowType type, BigDecimal amount, Frequency frequency, boolean paidByTenant) {}
}
