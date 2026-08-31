package com.smartbox.investory.investment.api.reporting;

import com.smartbox.investory.shared.projection.ProjectionSource;
import java.math.BigDecimal;

/** Stable black-box annual investment projection boundary. */
public interface InvestmentAnnualProjectionApi {
  AnnualProjection project(ProjectionRequest request);

  default CapitalProjection projectCapital(CapitalRequest request) {
    AnnualProjection annual =
        project(
            new ProjectionRequest(
                request.year(),
                request.startValue(),
                request.externalContribution(),
                request.annualReturnRate(),
                request.requestedWithdrawal(),
                request.source()));
    BigDecimal available =
        annual
            .startValue()
            .add(annual.externalContribution())
            .add(annual.annualReturnAmount())
            .max(BigDecimal.ZERO);
    return new CapitalProjection(
        annual.year(),
        annual.startValue(),
        annual.externalContribution(),
        BigDecimal.ZERO,
        annual.annualReturnAmount(),
        available,
        request.requestedWithdrawal(),
        annual.withdrawal(),
        annual.endValue(),
        annual.source());
  }

  record ProjectionRequest(
      int year,
      BigDecimal startValue,
      BigDecimal externalContribution,
      BigDecimal annualReturnRate,
      BigDecimal withdrawal,
      ProjectionSource source) {
    public ProjectionRequest(
        int year,
        BigDecimal startValue,
        BigDecimal annualReturnRate,
        BigDecimal withdrawal,
        ProjectionSource source) {
      this(year, startValue, BigDecimal.ZERO, annualReturnRate, withdrawal, source);
    }

    public ProjectionRequest {
      startValue = nz(startValue);
      externalContribution = nz(externalContribution).max(BigDecimal.ZERO);
      annualReturnRate = nz(annualReturnRate);
      withdrawal = nz(withdrawal).max(BigDecimal.ZERO);
      source = source == null ? ProjectionSource.PROJECTED : source;
    }
  }

  record AnnualProjection(
      int year,
      BigDecimal startValue,
      BigDecimal externalContribution,
      BigDecimal annualReturnAmount,
      BigDecimal withdrawal,
      BigDecimal endValue,
      ProjectionSource source) {
    public AnnualProjection(
        int year,
        BigDecimal startValue,
        BigDecimal annualReturnAmount,
        BigDecimal withdrawal,
        BigDecimal endValue,
        ProjectionSource source) {
      this(year, startValue, BigDecimal.ZERO, annualReturnAmount, withdrawal, endValue, source);
    }
  }

  record CapitalRequest(
      int year,
      BigDecimal startValue,
      BigDecimal externalContribution,
      BigDecimal annualReturnRate,
      BigDecimal requestedWithdrawal,
      ProjectionSource source) {
    public CapitalRequest(
        int year,
        BigDecimal startValue,
        BigDecimal annualReturnRate,
        BigDecimal requestedWithdrawal,
        ProjectionSource source) {
      this(year, startValue, BigDecimal.ZERO, annualReturnRate, requestedWithdrawal, source);
    }
  }

  record CapitalProjection(
      int year,
      BigDecimal startValue,
      BigDecimal externalContribution,
      BigDecimal annualIncome,
      BigDecimal annualReturn,
      BigDecimal availableForWithdrawal,
      BigDecimal requestedWithdrawal,
      BigDecimal actualWithdrawal,
      BigDecimal endValue,
      ProjectionSource source) {
    public BigDecimal unfundedRequest() {
      return requestedWithdrawal.subtract(actualWithdrawal).max(BigDecimal.ZERO);
    }
  }

  private static BigDecimal nz(BigDecimal value) {
    return com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull(value);
  }
}
