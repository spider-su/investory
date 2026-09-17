package com.smartbox.investory.profile.application;

import com.smartbox.investory.profile.api.model.EmploymentContext;
import com.smartbox.investory.profile.api.model.EmploymentPeriod;
import com.smartbox.investory.profile.api.model.EmploymentType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Resolves derived employment state without exposing persistence to accounting consumers. */
public class EmploymentContextResolver {
  public EmploymentContext resolve(List<EmploymentPeriod> periods, LocalDate date) {
    boolean uop =
        periods.stream().anyMatch(p -> p.type() == EmploymentType.UOP && p.activeOn(date));
    boolean jdg =
        periods.stream().anyMatch(p -> p.type() == EmploymentType.JDG && p.activeOn(date));
    return new EmploymentContext(uop, jdg);
  }

  public EmploymentContext resolve(List<EmploymentPeriod> periods, YearMonth month) {
    return resolve(periods, month.atDay(1));
  }
}
