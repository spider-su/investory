package com.smartbox.investory.marketradar.api;

import java.time.LocalDate;

public interface StrategistOutlookApi {
  Outlook outlook(LocalDate targetDate, LocalDate asOf);

  record Outlook(
      LocalDate targetDate,
      LocalDate asOf,
      Double actualYtdPercent,
      Double medianFullYearPercent,
      Double medianRemainingPercent,
      Double dispersionPercentagePoints,
      Double minFullYearPercent,
      Double maxFullYearPercent,
      Double p25FullYearPercent,
      Double p75FullYearPercent,
      Double priorMedianFullYearPercent,
      Double revisionPercentagePoints,
      int eligibleInstitutions,
      int staleExclusions,
      LocalDate oldestPublicationDate,
      LocalDate newestPublicationDate,
      String benchmarkStatus,
      ListRow[] forecasts,
      ListRow[] history,
      Accuracy accuracy,
      VixContext vix,
      LocalDate benchmarkQuoteDate,
      Double medianTargetLevel,
      Double minTargetLevel,
      Double maxTargetLevel,
      Double p25TargetLevel,
      Double p75TargetLevel,
      ConsensusPoint[] consensusTrend,
      Long lastUpdateAgeDays) {}

  record ListRow(
      String institution,
      String strategist,
      LocalDate publishedDate,
      LocalDate targetDate,
      double targetLevel,
      Double fullYearReturnPercent,
      Double remainingReturnPercent,
      String sourceTitle,
      String sourceUrl,
      String quality,
      String revision,
      Double errorPoints,
      Double errorPercent,
      LocalDate outcomeDate,
      Double indexLevelAtPublication,
      LocalDate publicationQuoteDate,
      Double impliedChangePercent) {}

  record Accuracy(
      int scoredForecasts,
      Double meanAbsoluteErrorPoints,
      Double meanAbsolutePercentageError,
      String sampleNotice) {}

  record VixContext(
      Double latest,
      LocalDate asOf,
      Double change5TradingDaysPercent,
      Double change20TradingDaysPercent,
      Double percentileOneYear,
      String status,
      String source,
      VixPoint[] trend) {}

  record ConsensusPoint(
      LocalDate asOf,
      Double medianTargetLevel,
      Double minTargetLevel,
      Double maxTargetLevel,
      Double dispersionPercentagePoints) {}

  record VixPoint(LocalDate date, double level) {}
}
