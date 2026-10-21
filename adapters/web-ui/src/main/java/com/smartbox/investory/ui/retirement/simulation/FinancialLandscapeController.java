package com.smartbox.investory.ui.retirement.simulation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Read-only visualization of existing profile and retirement planning facts. */
@Controller
@RequiredArgsConstructor
public class FinancialLandscapeController {
  private static final String COMPACT_VIEW = "compact";

  private final FinancialLandscapePageAssembler pageAssembler;

  @GetMapping("/portfolios/{portfolioId}/financial-landscape")
  public String landscape(
      @PathVariable Long portfolioId,
      @RequestParam(name = "view", defaultValue = "full") String view,
      Model model) {
    boolean compact = COMPACT_VIEW.equalsIgnoreCase(view);
    var page =
        compact ? pageAssembler.assemble(portfolioId, false) : pageAssembler.assemble(portfolioId);
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("landscape", page.asAttributes(compact));
    return compact ? "financial-landscape-compact" : "financial-landscape";
  }
}
