package com.smartbox.investory.ui.longterm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class LongTermDepositControllerTest {
  private LongTermAssetsClient assets;
  private LongTermDepositController controller;

  @BeforeEach
  void setUp() {
    assets = mock(LongTermAssetsClient.class);
    controller = new LongTermDepositController(assets);
  }

  @Test
  void formCarriesPortfolioContext() {
    var model = new ConcurrentModel();
    assertThat(controller.depositForm(7L, model)).isEqualTo("deposit-form");
    assertThat(model.getAttribute("portfolioId")).isEqualTo(7L);
  }

  @Test
  void createConvertsPercentagePointsAndRedirectsToAsset() {
    AssetView saved = mock(AssetView.class);
    when(saved.id()).thenReturn(9L);
    when(assets.createDeposit(any())).thenReturn(saved);
    var feedback = new RedirectAttributesModelMap();

    String target =
        controller.createDeposit(
            1L,
            "Deposit",
            CurrencyType.PLN,
            new BigDecimal("1000"),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2027, 1, 1),
            InterestTreatment.CAPITALIZE,
            new BigDecimal("5.5"),
            new BigDecimal("19"),
            "notes",
            feedback);

    assertThat(target).isEqualTo("redirect:/long-term-assets/9?portfolioId=1");
    verify(assets)
        .createDeposit(
            new DepositCommand(
                1L,
                "Deposit",
                CurrencyType.PLN,
                new BigDecimal("1000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                InterestTreatment.CAPITALIZE,
                new BigDecimal("0.055"),
                new BigDecimal("0.19"),
                "notes"));
  }

  @Test
  void createFailureReturnsSafeFeedback() {
    when(assets.createDeposit(any())).thenThrow(new IllegalArgumentException("bad deposit"));
    var feedback = new RedirectAttributesModelMap();
    String target =
        controller.createDeposit(
            1L,
            "Deposit",
            CurrencyType.PLN,
            BigDecimal.ONE,
            LocalDate.now(),
            LocalDate.now(),
            InterestTreatment.PAY_OUT,
            BigDecimal.ONE,
            new BigDecimal("19"),
            null,
            feedback);
    assertThat(target).isEqualTo("redirect:/long-term-assets?portfolioId=1");
    assertThat(feedback.getFlashAttributes().get("error")).isEqualTo("bad deposit");
  }

  @Test
  void detailUpdateConvertsRatesAndReportsSuccess() {
    var form = new LongTermDepositController.DepositDetailsForm();
    form.setMaturityDate(LocalDate.of(2028, 1, 1));
    form.setAnnualInterestRatePercent(new BigDecimal("4.25"));
    form.setTaxRatePercent(new BigDecimal("19"));
    form.setInterestTreatment(InterestTreatment.PAY_OUT);
    var feedback = new RedirectAttributesModelMap();

    controller.saveDepositDetails(5L, 1L, form, feedback);

    verify(assets)
        .saveDepositDetails(
            1L,
            5L,
            new DepositDetailsCommand(
                LocalDate.of(2028, 1, 1),
                new BigDecimal("0.0425"),
                new BigDecimal("0.19"),
                InterestTreatment.PAY_OUT));
    assertThat(feedback.getFlashAttributes()).doesNotContainKey("error");
  }
}
