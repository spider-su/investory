package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.LongTermAssetRateConversion;
import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Bond-specific MVC endpoints. */
@Controller
public class LongTermBondController {
  private final LongTermAssetsApi assets;
  private final PortfolioContextReader portfolios;

  public LongTermBondController(LongTermAssetsApi assets) {
    this(assets, null);
  }

  @Autowired
  public LongTermBondController(LongTermAssetsApi assets, PortfolioContextReader portfolios) {
    this.assets = assets;
    this.portfolios = portfolios;
  }

  @GetMapping("/portfolios/{portfolioId}/long-term-assets/new/bond")
  public String bondForm(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId, Model model) {
    BondForm asset = new BondForm();
    asset.setCurrency(
        portfolios == null
            ? CurrencyType.PLN
            : portfolios
                .findById(portfolioId)
                .map(context -> context.localCurrency())
                .orElse(CurrencyType.PLN));
    model.addAttribute("asset", asset);
    model.addAttribute("portfolioId", portfolioId);
    return "bond-form";
  }

  @GetMapping("/portfolios/{portfolioId}/long-term-assets/{id}/bond")
  public String editBond(@PathVariable Long portfolioId, @PathVariable Long id, Model model) {
    var asset = assets.bond(portfolioId, id);
    var form = new BondForm();
    form.setId(asset.id());
    form.setName(asset.name());
    form.setCurrency(asset.currency());
    form.setValue(asset.value());
    form.setAcquisitionDate(asset.acquisitionDate());
    form.setMaturityDate(asset.maturityDate());
    form.setAnnualRatePercent(LongTermAssetRateConversion.rateToPercent(asset.interestRate()));
    form.setNotes(asset.notes());
    model.addAttribute("asset", form);
    model.addAttribute("portfolioId", portfolioId);
    return "bond-form";
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/bond")
  public String createBond(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @ModelAttribute BondForm form,
      RedirectAttributes feedback) {
    try {
      var saved =
          assets.createBond(
              new BondCommand(
                  portfolioId,
                  null,
                  form.getName(),
                  form.getCurrency(),
                  form.getValue(),
                  form.getAcquisitionDate(),
                  LongTermAssetRateConversion.percentToRate(form.getAnnualRatePercent()),
                  form.getMaturityDate(),
                  form.getNotes()));
      return "redirect:/portfolios/" + portfolioId + "/long-term-assets";
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.assetError(exception));
      return "redirect:/portfolios/" + portfolioId + "/long-term-assets";
    }
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/bond")
  public String updateBond(
      @PathVariable Long id,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @ModelAttribute BondForm form,
      RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () ->
            assets.updateBond(
                new BondCommand(
                    portfolioId,
                    id,
                    form.getName(),
                    form.getCurrency(),
                    form.getValue(),
                    form.getAcquisitionDate(),
                    LongTermAssetRateConversion.percentToRate(form.getAnnualRatePercent()),
                    form.getMaturityDate(),
                    form.getNotes())),
        feedback);
    return "redirect:/portfolios/" + portfolioId + "/long-term-assets";
  }
}
