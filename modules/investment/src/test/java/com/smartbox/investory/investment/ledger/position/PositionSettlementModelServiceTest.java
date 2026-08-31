package com.smartbox.investory.investment.ledger.position;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PositionSettlementModelServiceTest {

  private final PositionSettlementModelService service = new PositionSettlementModelService();

  @Test
  void classifiesMarginOnlyXtbPositionAsResultOnly() {
    assertEquals(PositionSettlementModel.RESULT_ONLY, service.classifyXtb(null, null, 96.36, null));
  }

  @Test
  void explicitCfdProductWinsOverCashNotionalShape() {
    assertEquals(
        PositionSettlementModel.RESULT_ONLY,
        service.classifyXtb(1_000.0, 1_050.0, 100.0, "Equity CFD"));
  }

  @Test
  void classifiesCashNotionalXtbPositionAsCashSettled() {
    assertEquals(
        PositionSettlementModel.CASH_SETTLED, service.classifyXtb(1_000.0, 1_100.0, null, "Stock"));
  }

  @Test
  void failsClosedForPositionWithoutSettlementEvidence() {
    assertEquals(PositionSettlementModel.UNCLASSIFIED, service.classifyXtb(null, null, null, null));
  }
}
