package com.smartbox.investory.infrastructure.repository.account;

import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AccountMonthlyPerformanceId implements Serializable {
  private Long accountId;
  private LocalDate month;
}
