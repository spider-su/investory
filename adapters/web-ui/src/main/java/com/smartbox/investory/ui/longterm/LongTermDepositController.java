package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.LongTermAssetRateConversion;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Deposit-specific MVC endpoints kept separate from the general Long-Term asset page controller.
 */
@Controller
@RequiredArgsConstructor
public class LongTermDepositController {
  private final LongTermAssetsClient assets;

  @GetMapping("/long-term-assets/new/deposit")
  public String depositForm(@RequestParam Long portfolioId, Model model) {
    model.addAttribute("portfolioId", portfolioId);
    return "deposit-form";
  }

  @PostMapping("/long-term-assets/deposit")
  public String createDeposit(
      @RequestParam Long portfolioId,
      @RequestParam String name,
      @RequestParam CurrencyType currency,
      @RequestParam BigDecimal value,
      @RequestParam LocalDate acquisitionDate,
      @RequestParam LocalDate maturityDate,
      @RequestParam InterestTreatment interestTreatment,
      @RequestParam BigDecimal annualInterestRatePercent,
      @RequestParam BigDecimal taxRatePercent,
      @RequestParam(required = false) String notes,
      RedirectAttributes feedback) {
    try {
      var saved =
          assets.createDeposit(
              new DepositCommand(
                  portfolioId,
                  name,
                  currency,
                  value,
                  acquisitionDate,
                  maturityDate,
                  interestTreatment,
                  LongTermAssetRateConversion.percentToRate(annualInterestRatePercent),
                  LongTermAssetRateConversion.percentToRate(taxRatePercent),
                  notes));
      return LongTermAssetPageSupport.assetRedirect(saved.id(), portfolioId);
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.assetError(exception));
      return "redirect:/long-term-assets?portfolioId=" + portfolioId;
    }
  }

  @PostMapping("/long-term-assets/{id}/deposit-details")
  public String saveDepositDetails(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @ModelAttribute DepositDetailsForm form,
      RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () ->
            assets.saveDepositDetails(
                portfolioId,
                id,
                new DepositDetailsCommand(
                    form.maturityDate,
                    LongTermAssetRateConversion.percentToRate(form.annualInterestRatePercent),
                    LongTermAssetRateConversion.percentToRate(form.taxRatePercent),
                    form.interestTreatment)),
        feedback);
    return LongTermAssetPageSupport.assetRedirect(id, portfolioId);
  }

  @Getter
  @Setter
  public static class DepositDetailsForm {
    private LocalDate maturityDate;
    private BigDecimal annualInterestRatePercent;
    private BigDecimal taxRatePercent;
    private InterestTreatment interestTreatment;
  }
}
