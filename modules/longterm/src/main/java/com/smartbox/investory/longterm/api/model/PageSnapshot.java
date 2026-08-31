package com.smartbox.investory.longterm.api.model;

import java.util.List;

/** Public Long-Term API model. */
public record PageSnapshot(
    List<AssetSummaryView> assets, List<AssetGroupView> groups, AggregateView aggregate) {}
