package com.smartbox.investory.retirement.web;

import com.smartbox.investory.retirement.api.RetirementProfileApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST and in-process facade for retirement-owned profile summary data. */
@RestController
@RequestMapping("/api/v1/retirement/profile")
public class RetirementProfileRestController {
  private final RetirementProfileApi profile;

  public RetirementProfileRestController(
      @Qualifier("retirementProfileApplicationService") RetirementProfileApi profile) {
    this.profile = profile;
  }

  @GetMapping("/annual-cost")
  public com.smartbox.investory.retirement.api.model.AnnualCostView annualCost(
      @RequestParam Long portfolioId, @RequestParam CurrencyType reportingCurrency) {
    return profile.currentYearAnnualCost(portfolioId, reportingCurrency);
  }
}
