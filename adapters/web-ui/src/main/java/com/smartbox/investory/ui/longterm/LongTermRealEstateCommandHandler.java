package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.LongTermAssetRateConversion;
import com.smartbox.investory.longterm.api.model.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Handles real-estate mutations and their user feedback. */
@Component
final class LongTermRealEstateCommandHandler {
  private final LongTermAssetsClient assets;
  private final Clock clock;

  LongTermRealEstateCommandHandler(LongTermAssetsClient assets, Clock clock) {
    this.assets = assets;
    this.clock = clock;
  }

  void save(Long portfolioId, RealEstateForm e, BigDecimal growth, RedirectAttributes f) {
    try {
      assets.saveRealEstate(
          portfolioId,
          new RealEstateEntryModel(
              e.name(),
              e.currency(),
              e.acquisitionDate(),
              e.acquisitionValue(),
              e.currentValue(),
              e.taxBase(),
              e.monthlyRent(),
              e.monthlyParkingIncome(),
              e.monthlyAdministrationCost(),
              e.monthlyOtherCost(),
              e.annualPropertyTax(),
              e.annualInsurance(),
              e.effectiveFrom(),
              LongTermAssetRateConversion.percentToRate(growth),
              e.notes()));
    } catch (IllegalArgumentException | ResourceNotFoundException x) {
      f.addFlashAttribute("error", LongTermAssetPageSupport.assetError(x));
    }
  }

  void add(Long id, Long p, RentalContractForm form, BindingResult b, RedirectAttributes f) {
    if (b.hasErrors()) {
      LongTermAssetPageSupport.preserveBindingErrors(b, f);
      f.addFlashAttribute("showAddContract", true);
      return;
    }
    try {
      assets.createRentalContract(form.createCommand(p, id));
      f.addFlashAttribute("success", "Rental contract created.");
    } catch (IllegalArgumentException | ResourceNotFoundException x) {
      f.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(x));
      f.addFlashAttribute("rentalContract", form);
      f.addFlashAttribute("showAddContract", true);
    }
  }

  void update(
      Long id, Long c, Long p, RentalContractForm form, BindingResult b, RedirectAttributes f) {
    if (b.hasErrors()) {
      LongTermAssetPageSupport.preserveBindingErrors(b, f);
      f.addFlashAttribute("editContractId", c);
      return;
    }
    try {
      assets.updateRentalContract(form.updateCommand(p, id, c));
      f.addFlashAttribute("success", "Rental contract updated.");
    } catch (IllegalArgumentException | ResourceNotFoundException x) {
      f.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(x));
      f.addFlashAttribute("editContractId", c);
      f.addFlashAttribute("contractEditForm", form);
    }
  }

  void delete(Long id, Long c, Long p, RedirectAttributes f) {
    rental(() -> assets.deleteRentalContract(p, id, c), f, "Rental contract deleted.");
  }

  void end(Long id, Long c, Long p, LocalDate date, RedirectAttributes f) {
    rental(() -> assets.endRentalContract(p, id, c, date), f, "Expected contract end updated.");
  }

  void terminate(Long id, Long c, Long p, LocalDate date, RedirectAttributes f) {
    if (date.isAfter(LocalDate.now(clock))) {
      f.addFlashAttribute("error", "Actual termination date cannot be later than today.");
      return;
    }
    rental(() -> assets.terminateRentalContract(p, id, c, date), f, "Early termination recorded.");
  }

  void propertyGrowth(Long id, Long p, BigDecimal g, LocalDate from, RedirectAttributes f) {
    asset(
        () -> assets.savePropertyGrowth(p, id, LongTermAssetRateConversion.percentToRate(g), from),
        f);
  }

  void addValuation(
      Long id, Long p, LocalDate from, LocalDate to, BigDecimal g, RedirectAttributes f) {
    asset(
        () ->
            assets.addValuation(
                p,
                id,
                new ValuationCommand(from, to, LongTermAssetRateConversion.percentToRate(g))),
        f);
  }

  void updateValuation(
      Long id,
      Long period,
      Long p,
      LocalDate from,
      LocalDate to,
      BigDecimal g,
      RedirectAttributes f) {
    asset(
        () ->
            assets.updateValuation(
                p,
                id,
                period,
                new ValuationCommand(from, to, LongTermAssetRateConversion.percentToRate(g))),
        f);
  }

  void deleteValuation(Long id, Long period, Long p, RedirectAttributes f) {
    asset(() -> assets.deleteValuation(p, id, period), f);
  }

  private void rental(Runnable action, RedirectAttributes f, String success) {
    try {
      action.run();
      f.addFlashAttribute("success", success);
    } catch (IllegalArgumentException | ResourceNotFoundException x) {
      f.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(x));
    }
  }

  private void asset(Runnable action, RedirectAttributes f) {
    LongTermAssetPageSupport.applyAssetMutation(action, f);
  }
}
