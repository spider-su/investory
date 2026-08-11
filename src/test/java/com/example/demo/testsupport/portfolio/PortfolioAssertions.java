package com.example.demo.testsupport.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.OpenedPosition;
import java.util.List;

public final class PortfolioAssertions {

  private static final double EPSILON = 0.000001;

  private PortfolioAssertions() {}

  public static void expectCashToReconcile(
      double startingCash, List<CashOperation> operations, double expectedEndingCash) {
    double operationTotal =
        operations.stream()
            .map(CashOperation::getAmount)
            .filter(amount -> amount != null)
            .mapToDouble(Double::doubleValue)
            .sum();

    assertEquals(expectedEndingCash, startingCash + operationTotal, EPSILON);
  }

  public static void expectPositionToReconcile(
      double startingQuantity, List<OpenedPosition> openPositions, double expectedEndingQuantity) {
    double openQuantity =
        openPositions.stream()
            .map(OpenedPosition::getVolume)
            .filter(volume -> volume != null)
            .mapToDouble(Double::doubleValue)
            .sum();

    assertEquals(expectedEndingQuantity, startingQuantity + openQuantity, EPSILON);
  }

  public static void expectPortfolioValueToReconcile(
      double cash, double marketValue, double expectedPortfolioValue) {
    assertEquals(expectedPortfolioValue, cash + marketValue, EPSILON);
  }

  public static void expectInvestmentProfitToReconcile(
      double endingPortfolioValue,
      double startingPortfolioValue,
      double externalCashFlow,
      double expectedInvestmentProfit) {
    assertEquals(
        expectedInvestmentProfit,
        endingPortfolioValue - startingPortfolioValue - externalCashFlow,
        EPSILON);
  }

  public static void expectInternalTransferToBeNeutral(
      CashOperation transferOut, CashOperation transferIn) {
    assertNotNull(transferOut.getComment(), "transfer out should have a link/comment");
    assertEquals(transferOut.getComment(), transferIn.getComment());
    assertEquals(0.0, transferOut.getAmount() + transferIn.getAmount(), EPSILON);
  }

  public static void expectTradeToBeValueNeutralAtExecutionPrice(
      double cashDecrease, double positionCost) {
    assertEquals(positionCost, cashDecrease, EPSILON);
  }
}
