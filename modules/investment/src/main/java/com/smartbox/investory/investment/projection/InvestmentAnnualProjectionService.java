package com.smartbox.investory.investment.projection;

import com.smartbox.investory.investment.api.reporting.InvestmentAnnualProjectionApi;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** Owns investment return and end-value calculation. */
@Service
public class InvestmentAnnualProjectionService implements InvestmentAnnualProjectionApi {
  @Override
  public AnnualProjection project(ProjectionRequest request) {
    BigDecimal returnAmount = request.startValue().multiply(request.annualReturnRate());
    BigDecimal available =
        request
            .startValue()
            .add(request.externalContribution())
            .add(returnAmount)
            .max(BigDecimal.ZERO);
    BigDecimal withdrawal = request.withdrawal().min(available);
    BigDecimal end = available.subtract(withdrawal);
    return new AnnualProjection(
        request.year(),
        request.startValue(),
        request.externalContribution(),
        returnAmount,
        withdrawal,
        end,
        request.source());
  }
}
