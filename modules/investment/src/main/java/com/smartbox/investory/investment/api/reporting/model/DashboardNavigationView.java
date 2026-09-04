package com.smartbox.investory.investment.api.reporting.model;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Period navigation preserves an actual account subset, never an empty submitted selection. */
public record DashboardNavigationView(Long portfolioId, List<Long> accountIds) {

  public DashboardNavigationView {
    accountIds =
        accountIds == null
            ? List.of()
            : accountIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
  }

  public String periodUrl(DashboardPeriod period) {
    StringBuilder url =
        new StringBuilder("/dashboard?period=")
            .append(period.urlValue())
            .append("&portfolioId=")
            .append(portfolioId);
    if (accountIds.isEmpty()) {
      return url.toString();
    }
    url.append("&benchmarkAccountsSubmitted=true");
    accountIds.forEach(accountId -> url.append("&accountIds=").append(accountId));
    return url.toString();
  }
}
