package com.smartbox.investory.investment.ledger.position;

import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PositionSettlementModelService {

  private static final double EPSILON = 1.0e-9;

  public PositionSettlementModel classifyXtb(
      Double purchaseValue, Double saleValue, Double margin, String product) {
    boolean hasCashNotional = purchaseValue != null || saleValue != null;
    boolean hasMargin = margin != null && Math.abs(margin) > EPSILON;
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
