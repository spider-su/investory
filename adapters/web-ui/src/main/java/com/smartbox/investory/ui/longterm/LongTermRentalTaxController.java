package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.LongTermAssetRateConversion;
import com.smartbox.investory.longterm.api.model.*;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Rental-tax MVC endpoints. */
@Controller
@RequiredArgsConstructor
public class LongTermRentalTaxController {
  private final LongTermAssetsClient assets;

  @PostMapping("/long-term-assets/{id}/tax-base")
  public String updateTaxBase(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam BigDecimal taxBase,
      RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () -> assets.saveTaxBase(portfolioId, id, taxBase), feedback);
    return LongTermAssetPageSupport.assetRedirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/rental-tax-ownership")
  public String saveRentalTaxOwnership(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam(defaultValue = "false") boolean paidByTenant,
      RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () -> assets.saveRentalTaxOwnership(portfolioId, id, paidByTenant), feedback);
    return LongTermAssetPageSupport.assetRedirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/rental-tax-policy")
  public String saveRentalTaxPolicy(
      @RequestParam Long portfolioId,
      @ModelAttribute RentalTaxForm form,
      @RequestParam BigDecimal ratePercent,
      RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () ->
            assets.saveRentalTaxPolicy(
                portfolioId,
                new RentalTaxCommand(
                    form.getValidFrom(),
                    form.getValidTo(),
                    LongTermAssetRateConversion.percentToRate(ratePercent))),
        feedback);
    return "redirect:/long-term-assets?portfolioId=" + portfolioId;
  }

  @PostMapping("/long-term-assets/rental-tax-policy/{policyId}")
  public String updateRentalTaxPolicy(
      @PathVariable Long policyId,
      @RequestParam Long portfolioId,
      @ModelAttribute RentalTaxForm form,
      @RequestParam BigDecimal ratePercent,
      RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () ->
            assets.updateRentalTaxPolicy(
                portfolioId,
                policyId,
                new RentalTaxCommand(
                    form.getValidFrom(),
                    form.getValidTo(),
                    LongTermAssetRateConversion.percentToRate(ratePercent))),
        feedback);
    return LongTermAssetPageSupport.taxPolicyRedirect(portfolioId);
  }

  @PostMapping("/long-term-assets/rental-tax-policy/{policyId}/delete")
  public String deleteRentalTaxPolicy(
      @PathVariable Long policyId, @RequestParam Long portfolioId, RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () -> assets.deleteRentalTaxPolicy(portfolioId, policyId), feedback);
    return LongTermAssetPageSupport.taxPolicyRedirect(portfolioId);
  }
}
