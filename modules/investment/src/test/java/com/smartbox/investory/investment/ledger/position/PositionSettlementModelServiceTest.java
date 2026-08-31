package com.smartbox.investory.investment.ledger.position;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Position Settlement Model Service")
class PositionSettlementModelServiceTest {

  private final PositionSettlementModelService service = new PositionSettlementModelService();

  @DisplayName("classifies Margin Only Xtb Position As Result Only")
  @Test
  void classifiesMarginOnlyXtbPositionAsResultOnly() {
    assertEquals(
        PositionSettlementModel.RESULT_ONLY,
        service.classifyXtb(null, null, BigDecimal.valueOf(96.36), null));
  }

  @DisplayName("explicit Cfd Product Wins Over Cash Notional Shape")
  @Test
  void explicitCfdProductWinsOverCashNotionalShape() {
    assertEquals(
        PositionSettlementModel.RESULT_ONLY,
        service.classifyXtb(
            BigDecimal.valueOf(1_000),
            BigDecimal.valueOf(1_050),
            BigDecimal.valueOf(100),
            "Equity CFD"));
  }

  @DisplayName("classifies Cash Notional Xtb Position As Cash Settled")
  @Test
  void classifiesCashNotionalXtbPositionAsCashSettled() {
    assertEquals(
        PositionSettlementModel.CASH_SETTLED,
        service.classifyXtb(BigDecimal.valueOf(1_000), BigDecimal.valueOf(1_100), null, "Stock"));
  }

  @DisplayName("fails Closed For Position Without Settlement Evidence")
  @Test
  void failsClosedForPositionWithoutSettlementEvidence() {
    assertEquals(PositionSettlementModel.UNCLASSIFIED, service.classifyXtb(null, null, null, null));
  }
}
