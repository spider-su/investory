package com.smartbox.investory.ui.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class FinancialLandscapeControllerTest {
  private final FinancialLandscapePageAssembler pageAssembler =
      mock(FinancialLandscapePageAssembler.class);
  private final FinancialLandscapeController controller =
      new FinancialLandscapeController(pageAssembler);

  @BeforeEach
  void setup() {
    when(pageAssembler.assemble(7L))
        .thenReturn(
            new FinancialLandscapePageView(
                7L,
                "PLN",
                2026,
                new BigDecimal("253100"),
                new BigDecimal("240000"),
                new BigDecimal("63100"),
                new BigDecimal("180000"),
                BigDecimal.ZERO,
                new BigDecimal("120000"),
                new BigDecimal("850000"),
                List.of(
                    new FinancialLandscapePageView.ExpenseGroupView(
                        "Housing", new BigDecimal("6000")))));
  }

  @Test
  void delegatesToPageAssemblerAndWiresTheModel() {
    var model = new ConcurrentModel();
    assertThat(controller.landscape(7L, "full", model)).isEqualTo("financial-landscape");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) model.getAttribute("landscape");
    assertThat(result)
        .containsEntry("annualIncome", new BigDecimal("253100"))
        .containsEntry("annualCashIncome", new BigDecimal("240000"))
        .containsEntry("annualMarketResult", new BigDecimal("63100"))
        .containsEntry("annualExpenses", new BigDecimal("180000"))
        .containsEntry("annualGap", BigDecimal.ZERO)
        .containsEntry("liquidAssets", new BigDecimal("120000"))
        .containsEntry("illiquidAssets", new BigDecimal("850000"))
        .containsKey("expenseGroups");
    assertThat(result)
        .hasEntrySatisfying(
            "expenseGroups",
            value -> {
              assertThat(value).isInstanceOf(List.class);
              assertThat((List<?>) value).hasSize(1);
            });
    verify(pageAssembler).assemble(7L);
  }

  @Test
  void compactUsesSameDataAdapter() {
    when(pageAssembler.assemble(7L, false))
        .thenReturn(
            new FinancialLandscapePageView(
                7L, "PLN", 2026, null, null, null, null, null, null, null, List.of()));
    var model = new ConcurrentModel();
    assertThat(controller.landscape(7L, "compact", model)).isEqualTo("financial-landscape-compact");
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) model.getAttribute("landscape");
    assertThat(data)
        .containsOnlyKeys(
            "annualIncome",
            "annualCashIncome",
            "annualMarketResult",
            "annualExpenses",
            "annualGap");
    verify(pageAssembler).assemble(7L, false);
  }
}
