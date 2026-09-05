package com.smartbox.investory.ui.longterm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.BondView;
import com.smartbox.investory.longterm.api.model.CashReserveCommand;
import com.smartbox.investory.longterm.api.model.CashReserveView;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ui.ConcurrentModel;

class LongTermFixedIncomeFormControllerTest {
  private static final Long PORTFOLIO_ID = 7L;
  private static final LocalDate ACQUIRED = LocalDate.of(2024, 8, 1);

  @Test
  void cashEditPrepopulatesAcquisitionAndPercentageRateAndSaveUsesCanonicalRate() {
    LongTermAssetsApi assets = mock(LongTermAssetsApi.class);
    when(assets.cashReserve(PORTFOLIO_ID, 42L)).thenReturn(cash());
    LongTermAssetController controller = new LongTermAssetController(assets, fixedClock());
    var model = new ConcurrentModel();

    controller.editCashReserve(PORTFOLIO_ID, 42L, model);
    CashReserveForm form = (CashReserveForm) model.getAttribute("asset");
    assertThat(form.getAcquisitionDate()).isEqualTo(ACQUIRED);
    assertThat(form.getInterestRate()).isEqualByComparingTo("4.0");

    form.setName("Renamed");
    form.setInterestRate(new BigDecimal("4.0"));
    controller.saveCashReserve(PORTFOLIO_ID, form, mockRedirectAttributes());
    ArgumentCaptor<CashReserveCommand> command = ArgumentCaptor.forClass(CashReserveCommand.class);
    verify(assets).updateCashReserve(command.capture());
    assertThat(command.getValue().acquisitionDate()).isEqualTo(ACQUIRED);
    assertThat(command.getValue().interestRate()).isEqualByComparingTo("0.04");
  }

  @Test
  void bondEditPrepopulatesAcquisitionAndPercentageRate() {
    LongTermAssetsApi assets = mock(LongTermAssetsApi.class);
    when(assets.bond(PORTFOLIO_ID, 41L)).thenReturn(bond());
    var model = new ConcurrentModel();

    new LongTermBondController(assets).editBond(PORTFOLIO_ID, 41L, model);
    BondForm form = (BondForm) model.getAttribute("asset");
    assertThat(form.getAcquisitionDate()).isEqualTo(LocalDate.of(2024, 7, 31));
    assertThat(form.getAnnualRatePercent()).isEqualByComparingTo("5.9");
  }

  private static CashReserveView cash() {
    return new CashReserveView(
        42L,
        PORTFOLIO_ID,
        "Cash",
        CurrencyType.PLN,
        ACQUIRED,
        new BigDecimal("50000"),
        new BigDecimal("0.04"),
        LocalDate.of(2027, 8, 1),
        true,
        "notes");
  }

  private static BondView bond() {
    return new BondView(
        41L,
        PORTFOLIO_ID,
        "Bond",
        CurrencyType.PLN,
        LocalDate.of(2024, 7, 31),
        new BigDecimal("10000"),
        new BigDecimal("0.059"),
        LocalDate.of(2026, 2, 28),
        true,
        "notes");
  }

  private static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneOffset.UTC);
  }

  private static org.springframework.web.servlet.mvc.support.RedirectAttributes
      mockRedirectAttributes() {
    return mock(org.springframework.web.servlet.mvc.support.RedirectAttributes.class);
  }
}
