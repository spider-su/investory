package com.smartbox.investory.retirement.web;

import com.smartbox.investory.retirement.api.RetirementPreviewApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.EditorPreviewResponse;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Stable HTTP adapter for plan-editor previews. */
@RestController
@Validated
@RequestMapping("/api/v1/retirement/portfolios/{portfolioId}/preview")
public class RetirementPreviewRestController {
  private final RetirementPreviewApi previews;
  private final PortfolioContextReader portfolios;

  public RetirementPreviewRestController(
      RetirementPreviewApi previews, PortfolioContextReader portfolios) {
    this.previews = previews;
    this.portfolios = portfolios;
  }

  @PostMapping
  public EditorPreviewResponse editorPreview(
      @PathVariable @NotNull Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @Valid @RequestBody PlanEditorInput request) {
    CurrencyType currency =
        planningDisplayCurrency != null
            ? planningDisplayCurrency
            : portfolios
                .findById(portfolioId)
                .map(context -> context.localCurrency())
                .orElse(CurrencyType.PLN);
    return previews.editorPreview(portfolioId, planId, currency, request);
  }
}
