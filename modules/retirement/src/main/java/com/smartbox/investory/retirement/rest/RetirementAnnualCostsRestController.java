package com.smartbox.investory.retirement.rest;

import com.smartbox.investory.retirement.api.RetirementAnnualCostsApi;
import com.smartbox.investory.retirement.api.RetirementAnnualCostsApi.AnnualCosts;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST and in-process facade for editable retirement annual costs. */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/retirement/plans/{planId}/annual-costs")
public class RetirementAnnualCostsRestController {
  private final RetirementAnnualCostsApi annualCosts;

  public RetirementAnnualCostsRestController(RetirementAnnualCostsApi annualCosts) {
    this.annualCosts = annualCosts;
  }

  @GetMapping
  public AnnualCosts load(
      @PathVariable Long portfolioId, @PathVariable Long planId, @RequestParam int year) {
    return annualCosts.load(portfolioId, planId, year);
  }

  @PutMapping("/groups")
  public AnnualCosts saveGroup(
      @PathVariable Long portfolioId,
      @PathVariable Long planId,
      @RequestParam int year,
      @RequestBody SaveGroupRequest request) {
    return annualCosts.saveGroup(
        portfolioId, planId, year, request.groupId(), request.name(), request.monthlyAmount());
  }

  @DeleteMapping("/groups/{groupId}")
  public AnnualCosts deleteGroup(
      @PathVariable Long portfolioId,
      @PathVariable Long planId,
      @RequestParam int year,
      @PathVariable Long groupId) {
    return annualCosts.deleteGroup(portfolioId, planId, year, groupId);
  }

  @PutMapping("/annual-extras")
  public AnnualCosts saveAnnualExtras(
      @PathVariable Long portfolioId,
      @PathVariable Long planId,
      @RequestParam int year,
      @RequestBody BigDecimal annualAmount) {
    return annualCosts.saveAnnualExtras(portfolioId, planId, year, annualAmount);
  }

  public record SaveGroupRequest(Long groupId, String name, BigDecimal monthlyAmount) {}
}
