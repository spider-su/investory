package com.smartbox.investory.investment.ledger.position;

import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PositionSettlementModelService {

  private static final BigDecimal EPSILON = new BigDecimal("0.000000001");

  public PositionSettlementModel classifyXtb(
      BigDecimal purchaseValue, BigDecimal saleValue, BigDecimal margin, String product) {
    boolean hasCashNotional = purchaseValue != null || saleValue != null;
    boolean hasMargin = margin != null && margin.abs().compareTo(EPSILON) > 0;
    boolean explicitCfd =
        StringUtils.hasText(product) && product.trim().toUpperCase(Locale.ROOT).contains("CFD");

    if (explicitCfd || (hasMargin && !hasCashNotional)) {
      return PositionSettlementModel.RESULT_ONLY;
    }
    if (hasCashNotional) {
      return PositionSettlementModel.CASH_SETTLED;
    }
    return PositionSettlementModel.UNCLASSIFIED;
  }
}
