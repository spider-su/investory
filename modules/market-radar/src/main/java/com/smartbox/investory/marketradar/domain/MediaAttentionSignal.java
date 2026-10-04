package com.smartbox.investory.marketradar.domain;

public record MediaAttentionSignal(
    String symbol,
    long mentions7d,
    long previousMentions7d,
    long independentSources7d,
    long bullish7d,
    long neutral7d,
    long bearish7d,
    double attentionRatio) {}
