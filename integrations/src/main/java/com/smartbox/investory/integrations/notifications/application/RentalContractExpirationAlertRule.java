package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.RentalContractProjectionModel;
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
public class RentalContractExpirationAlertRule implements AlertRule {
  private final LongTermAssetProfileReader longTerm;
  private final NotificationProperties properties;
  private final Clock clock;

  @Override public String code() { return "RENTAL_CONTRACT_EXPIRING"; }
  @Override public Optional<String> evaluate() { return Optional.empty(); }

  @Override
  public List<AlertObservation> evaluateObservations() {
    if (!properties.isRentalContractExpirationEnabled()) return List.of();
    LocalDate today = LocalDate.now(clock);
    List<AlertObservation> result = new ArrayList<>();
    for (LongTermAssetProjectionModel asset : longTerm.snapshot(properties.getPortfolioId(), today).projectionInputs()) {
      for (RentalContractProjectionModel contract : asset.rentalContracts()) {
        LocalDate end = effectiveEnd(contract);
        if (end == null) continue;
        long days = ChronoUnit.DAYS.between(today, end);
        if (days < 0 || days > properties.getRentalContractExpirationDays()) continue;
        result.add(new AlertObservation(
            asset.id() + ":" + contract.id() + ":" + end,
            asset.name() + " rental contract expires " + end + " (" + days + " days)."));
      }
    }
    return result;
  }

  private static LocalDate effectiveEnd(RentalContractProjectionModel contract) {
    if (contract.endDate() == null) return contract.terminatedDate();
    if (contract.terminatedDate() == null) return contract.endDate();
    return contract.endDate().isBefore(contract.terminatedDate()) ? contract.endDate() : contract.terminatedDate();
  }
}
