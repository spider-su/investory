package com.smartbox.investory.longterm.web;

import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.BondCommand;
import com.smartbox.investory.longterm.api.model.BondView;
import com.smartbox.investory.longterm.api.model.CashReserveCommand;
import com.smartbox.investory.longterm.api.model.CashReserveView;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for factual Long-Term fixed-income asset round trips. */
@RestController
@Validated
@RequestMapping("/api/v1/portfolios/{portfolioId}/long-term-assets")
@RequiredArgsConstructor
public class LongTermAssetsRestController {
  private final LongTermAssetsApi assets;

  @GetMapping("/bond/{id}")
  public BondView bond(@PathVariable @Positive Long portfolioId, @PathVariable @Positive Long id) {
    return assets.bond(portfolioId, id);
  }

  @PutMapping("/bond/{id}")
  public BondView updateBond(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @RequestBody BondCommand command) {
    return assets.updateBond(
        new BondCommand(
            portfolioId,
            id,
            command.name(),
            command.currency(),
            command.value(),
            command.acquisitionDate(),
            command.interestRate(),
            command.maturityDate(),
            command.notes()));
  }

  @GetMapping("/cash-reserve/{id}")
  public CashReserveView cashReserve(
      @PathVariable @Positive Long portfolioId, @PathVariable @Positive Long id) {
    return assets.cashReserve(portfolioId, id);
  }

  @PutMapping("/cash-reserve/{id}")
  public CashReserveView updateCashReserve(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @RequestBody CashReserveCommand command) {
    return assets.updateCashReserve(
        new CashReserveCommand(
            portfolioId,
            id,
            command.name(),
            command.currency(),
            command.value(),
            command.acquisitionDate(),
            command.interestRate(),
            command.maturityDate(),
            command.notes()));
  }
}
