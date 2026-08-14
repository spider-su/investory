package com.smartbox.investory.services.notifications;

import com.smartbox.investory.infrastructure.repository.notifications.DrawdownAlertState;
import com.smartbox.investory.infrastructure.repository.notifications.DrawdownAlertStateRepository;
import com.smartbox.investory.services.PortfolioService;
import com.smartbox.investory.services.models.Portfolio;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Fires when current total P/L vs all-time peak drops by more than the configured percentage. The
 * "peak" here is approximated from the running balance (deposits net of withdrawals + total P/L).
 */
@Component
@RequiredArgsConstructor
public class DrawdownAlertRule implements AlertRule {

  private final PortfolioService portfolioService;
  private final NotificationProperties properties;
  private final DrawdownAlertStateRepository stateRepository;
  private final Clock clock;

  @Override
  public String code() {
    return "DRAWDOWN";
  }

  @Override
  public Optional<String> evaluate() {
    Portfolio p = portfolioService.calculateTotalProfitLoss();
    double equity = p.getBalance();
    DrawdownAlertState state =
        stateRepository
            .findById(DrawdownAlertState.SINGLETON_ID)
            .orElseGet(DrawdownAlertState::new);
    if (equity > state.getPeakEquity()) {
      state.setPeakEquity(equity);
      stateRepository.save(state);
      return Optional.empty();
    }
    if (state.getPeakEquity() <= 0.0) {
      return Optional.empty();
    }
    double drawdownPct = (state.getPeakEquity() - equity) / state.getPeakEquity() * 100.0;
    ZonedDateTime now = ZonedDateTime.now(clock);
    if (drawdownPct >= properties.getDrawdownThresholdPct() && cooldownElapsed(state, now)) {
      state.setLastAlertAt(now);
      stateRepository.save(state);
      return Optional.of(
          String.format(
              "Drawdown alert: %.1f%% below peak (peak %,.0f %s, now %,.0f %s)",
              drawdownPct,
              state.getPeakEquity(),
              p.getBaseCurrency(),
              equity,
              p.getBaseCurrency()));
    }
    return Optional.empty();
  }

  private boolean cooldownElapsed(DrawdownAlertState state, ZonedDateTime now) {
    if (state.getLastAlertAt() == null) {
      return true;
    }
    return !now.isBefore(
        state
            .getLastAlertAt()
            .plus(Duration.ofHours(Math.max(0, properties.getDrawdownCooldownHours()))));
  }
}
