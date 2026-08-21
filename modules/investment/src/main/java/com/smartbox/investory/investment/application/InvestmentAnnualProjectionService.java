package com.smartbox.investory.investment.application;

import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** Owns investment return and end-value calculation. */
@Service
public class InvestmentAnnualProjectionService implements InvestmentAnnualProjectionApi {
  @Override
  public AnnualProjection project(ProjectionRequest request) {
    BigDecimal returnAmount = request.startValue().multiply(request.annualReturnRate());
    BigDecimal end = request.startValue().add(returnAmount).subtract(request.withdrawal()).max(BigDecimal.ZERO);
    return new AnnualProjection(
        request.year(), request.startValue(), returnAmount, request.withdrawal(), end, request.source());
  }
}
