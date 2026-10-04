package com.smartbox.investory.marketradar.domain;

import java.util.List;

public record ThemeDefinition(String name, String proxySymbol, List<String> members) {
  public ThemeDefinition {
    members = List.copyOf(members);
  }
}
