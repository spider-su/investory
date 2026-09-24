package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.model.PersonalAssetCommand;
import com.smartbox.investory.longterm.api.model.ResourceNotFoundException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class LongTermPersonalAssetController {
  private final LongTermAssetsClient assets;
  private final Clock clock;

  @GetMapping("/portfolios/{portfolioId}/long-term-assets/new/personal-asset")
  public String form(@PathVariable Long portfolioId, Model model) {
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute(
        "categories", com.smartbox.investory.longterm.api.model.PersonalAssetCategory.values());
    return "personal-asset-form";
  }

  @GetMapping("/portfolios/{portfolioId}/long-term-assets/{id}/personal-asset")
  public String edit(@PathVariable Long portfolioId, @PathVariable Long id, Model model) {
    var asset = assets.personalAsset(portfolioId, id);
    var form = new PersonalAssetForm();
    form.setId(asset.id());
    form.setName(asset.name());
    form.setCategory(asset.category());
    form.setCurrency(asset.currency());
    form.setValue(asset.value());
    form.setAcquisitionDate(asset.acquisitionDate());
    form.setNotes(asset.notes());
    model.addAttribute("asset", form);
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute(
        "categories", com.smartbox.investory.longterm.api.model.PersonalAssetCategory.values());
    return "personal-asset-form";
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/personal-asset")
  public String save(
      @PathVariable Long portfolioId,
      @ModelAttribute PersonalAssetForm form,
      RedirectAttributes feedback) {
    try {
      var command =
          new PersonalAssetCommand(
              portfolioId,
              form.getId(),
              form.getName(),
              form.getCategory(),
              form.getCurrency(),
              form.getValue(),
              form.getAcquisitionDate(),
              form.getNotes());
      if (form.getId() == null) assets.createPersonalAsset(command);
      else assets.updatePersonalAsset(command);
      return "redirect:/portfolios/" + portfolioId + "/long-term-assets";
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.assetError(exception));
      return "redirect:/portfolios/" + portfolioId + "/long-term-assets";
    }
  }
}
