package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.api.InvestmentProfileFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class InvestmentProfileController {
  private final InvestmentProfileFacade facade;

  @GetMapping("/investment-profile")
  public String profile(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    var profile = facade.loadProfile(portfolioId);
    model.addAttribute("profile", profile);
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("profileHeaderNetWorth", profile.totalNetWorthCompactDisplay());
    model.addAttribute("profileHeaderLiquid", profile.liquidAssetsCompactDisplay());
    model.addAttribute(
        "profileHeaderLiquidMeta", profile.liquidAssetsPercentageDisplay() + " of net worth");
    model.addAttribute("profileHeaderIncome", profile.expectedLongTermAssetIncomeCompactDisplay());
    model.addAttribute("profileHeaderCurrency", profile.currency());
    return "investment-profile";
  }
}
