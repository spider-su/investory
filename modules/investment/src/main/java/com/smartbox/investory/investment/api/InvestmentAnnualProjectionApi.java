package com.smartbox.investory.investment.api;

import java.math.BigDecimal;

/** Stable black-box annual investment projection boundary. */
public interface InvestmentAnnualProjectionApi {
  AnnualProjection project(ProjectionRequest request);

  default CapitalProjection projectCapital(CapitalRequest request) {
    AnnualProjection annual = project(new ProjectionRequest(
        request.year(), request.startValue(), request.externalContribution(), request.annualReturnRate(), request.requestedWithdrawal(), request.source()));
    BigDecimal available = annual.startValue().add(annual.externalContribution()).add(annual.annualReturnAmount()).max(BigDecimal.ZERO);
    return new CapitalProjection(annual.year(), annual.startValue(), annual.externalContribution(),
        BigDecimal.ZERO,
        annual.annualReturnAmount(), available, request.requestedWithdrawal(), annual.withdrawal(),
        annual.endValue(), Source.valueOf(annual.source().name()));
  }

  record ProjectionRequest(
      int year, BigDecimal startValue, BigDecimal externalContribution, BigDecimal annualReturnRate,
      BigDecimal withdrawal, Source source) {
    public ProjectionRequest(
        int year, BigDecimal startValue, BigDecimal annualReturnRate, BigDecimal withdrawal, Source source) {
      this(year, startValue, BigDecimal.ZERO, annualReturnRate, withdrawal, source);
    }
    public ProjectionRequest {
      startValue = nz(startValue);
      externalContribution = nz(externalContribution).max(BigDecimal.ZERO);
      annualReturnRate = nz(annualReturnRate);
      withdrawal = nz(withdrawal).max(BigDecimal.ZERO);
      source = source == null ? Source.PROJECTED : source;
    }
  }

  record AnnualProjection(
      int year,
      BigDecimal startValue,
      BigDecimal externalContribution,
      BigDecimal annualReturnAmount,
      BigDecimal withdrawal,
      BigDecimal endValue,
      Source source) {
    public AnnualProjection(
        int year, BigDecimal startValue, BigDecimal annualReturnAmount, BigDecimal withdrawal,
        BigDecimal endValue, Source source) {
      this(year, startValue, BigDecimal.ZERO, annualReturnAmount, withdrawal, endValue, source);
    }
  }

  record CapitalRequest(
      int year, BigDecimal startValue, BigDecimal externalContribution, BigDecimal annualReturnRate,
      BigDecimal requestedWithdrawal, Source source) {
    public CapitalRequest(
        int year, BigDecimal startValue, BigDecimal annualReturnRate,
        BigDecimal requestedWithdrawal, Source source) {
      this(year, startValue, BigDecimal.ZERO, annualReturnRate, requestedWithdrawal, source);
    }
  }

  record CapitalProjection(
      int year, BigDecimal startValue, BigDecimal externalContribution, BigDecimal annualIncome, BigDecimal annualReturn,
      BigDecimal availableForWithdrawal, BigDecimal requestedWithdrawal,
      BigDecimal actualWithdrawal, BigDecimal endValue, Source source) {
    public BigDecimal unfundedRequest() {
      return requestedWithdrawal.subtract(actualWithdrawal).max(BigDecimal.ZERO);
    }
  }

  enum Source {
    ACTUAL,
    PROJECTED
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
