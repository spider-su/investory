package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.shared.assets.AssetEconomicCategory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BondMaturityAlertRule implements AlertRule {
  private final LongTermAssetProfileReader longTerm;
  private final NotificationProperties properties;
  private final Clock clock;

  @Override public String code() { return "BOND_MATURITY_APPROACHING"; }
  @Override public Optional<String> evaluate() { return Optional.empty(); }

  @Override
  public List<AlertObservation> evaluateObservations() {
    if (!properties.isBondMaturityEnabled()) return List.of();
    LocalDate today = LocalDate.now(clock);
    List<AlertObservation> result = new ArrayList<>();
    for (LongTermAssetProjectionModel asset : longTerm.snapshot(properties.getPortfolioId(), today).projectionInputs()) {
      if (asset.category() != AssetEconomicCategory.FIXED_INCOME || asset.maturityDate() == null) continue;
      long days = ChronoUnit.DAYS.between(today, asset.maturityDate());
      if (days < 0 || days > properties.getBondMaturityDays()) continue;
      result.add(new AlertObservation(
          asset.id() + ":" + asset.maturityDate(),
          asset.name() + " matures " + asset.maturityDate() + " (" + days + " days)."));
    }
    return result;
  }
}
