package com.smartbox.investory.integrations.notifications.application;

/** One currently-active incident observed by an alert rule. */
public record AlertObservation(String key, String message) {}
