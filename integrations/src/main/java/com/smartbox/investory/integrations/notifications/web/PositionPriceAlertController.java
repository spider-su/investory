package com.smartbox.investory.integrations.notifications.web;

import com.smartbox.investory.integrations.notifications.application.PositionPriceAlertService;
import com.smartbox.investory.integrations.notifications.application.PositionPriceAlertService.HistoryItem;
import com.smartbox.investory.integrations.notifications.application.PositionPriceAlertService.PositionView;
import com.smartbox.investory.integrations.notifications.application.PositionPriceAlertService.UpdateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/portfolios/{portfolioId}/position-alerts")
public class PositionPriceAlertController {
  private final PositionPriceAlertService alerts;

  @GetMapping("/{symbol}")
  public PositionView get(@PathVariable Long portfolioId, @PathVariable String symbol) {
    return alerts.get(portfolioId, symbol);
  }

  @PutMapping("/{symbol}")
  public PositionView update(@PathVariable Long portfolioId, @PathVariable String symbol,
                             @RequestBody UpdateRequest request) {
    return alerts.update(portfolioId, symbol, request);
  }

  @GetMapping("/{symbol}/history")
  public List<HistoryItem> history(@PathVariable Long portfolioId,
                                   @PathVariable String symbol) {
    return alerts.history(portfolioId, symbol);
  }
}
