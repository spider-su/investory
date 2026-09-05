package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.LongTermAssetRateConversion;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Bond-specific MVC endpoints. */
@Controller
public class LongTermBondController {
  private final LongTermAssetsClient assets;
  private final PortfolioContextReader portfolios;

  public LongTermBondController(LongTermAssetsClient assets) {
    this(assets, null);
  }

  @Autowired
  public LongTermBondController(LongTermAssetsClient assets, PortfolioContextReader portfolios) {
    this.assets = assets;
    this.portfolios = portfolios;
  }

  @GetMapping("/portfolios/{portfolioId}/long-term-assets/new/bond")
  public String bondForm(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId, Model model) {
    LongTermAssetForm asset = new LongTermAssetForm();
    asset.setPortfolioId(portfolioId);
    asset.setType(LongTermAssetType.BOND);
    asset.setCurrency(
        portfolios == null
            ? CurrencyType.PLN
            : portfolios
                .findById(portfolioId)
                .map(c -> c.localCurrency())
                .orElse(CurrencyType.PLN));
    asset.setActive(true);
    model.addAttribute("asset", asset);
    model.addAttribute("portfolioId", portfolioId);
    return "bond-form";
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/bond")
  public String createBond(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @RequestParam String name,
      @RequestParam CurrencyType currency,
      @RequestParam BigDecimal value,
      @RequestParam LocalDate acquisitionDate,
      @RequestParam LocalDate maturityDate,
      @RequestParam InterestTreatment interestTreatment,
      @RequestParam BigDecimal annualRatePercent,
      @RequestParam(required = false) String notes,
      RedirectAttributes feedback) {
    try {
      var saved =
          assets.createBond(
              new BondCommand(
                  portfolioId,
                  null,
                  name,
                  currency,
                  value,
                  acquisitionDate,
                  maturityDate,
                  interestTreatment,
                  LongTermAssetRateConversion.percentToRate(annualRatePercent),
                  notes));
      return LongTermAssetPageSupport.assetRedirect(saved.id(), portfolioId);
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.assetError(exception));
      return "redirect:/portfolios/" + portfolioId + "/long-term-assets";
    }
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/bond-details")
  public String saveBondDetails(
      @PathVariable Long id,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @ModelAttribute BondDetailsForm form,
      RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () ->
            assets.saveBondDetails(
                portfolioId,
                id,
                new BondDetailsCommand(
                    form.maturityDate,
                    LongTermAssetRateConversion.percentToRate(form.taxRatePercent),
                    form.interestTreatment,
                    form.redemptionValue)),
        feedback);
    return LongTermAssetPageSupport.assetRedirect(id, portfolioId);
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/bond")
  public String updateBond(
      @PathVariable Long id,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @RequestParam String name,
      @RequestParam CurrencyType currency,
      @RequestParam BigDecimal value,
      @RequestParam LocalDate acquisitionDate,
      @RequestParam LocalDate maturityDate,
      @RequestParam InterestTreatment interestTreatment,
      @RequestParam BigDecimal annualRatePercent,
      @RequestParam(required = false) String notes,
      RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () ->
            assets.updateBond(
                new BondCommand(
                    portfolioId,
                    id,
                    name,
                    currency,
                    value,
                    acquisitionDate,
                    maturityDate,
                    interestTreatment,
                    LongTermAssetRateConversion.percentToRate(annualRatePercent),
                    notes)),
        feedback);
    return LongTermAssetPageSupport.assetRedirect(id, portfolioId);
  }

  @Getter
  @Setter
  public static class BondDetailsForm {
    private LocalDate maturityDate;
    private BigDecimal taxRatePercent;
    private BigDecimal redemptionValue;
    private InterestTreatment interestTreatment;
  }
}
