package com.smartbox.investory.investment.imports;

import com.smartbox.investory.investment.api.importing.ImportSource;

public enum ImportSourceType {
  MANUAL,
  API,
  TELEGRAM;

  public static ImportSourceType fromApi(ImportSource source) {
    return switch (source) {
      case MANUAL -> MANUAL;
      case API -> API;
      case TELEGRAM -> TELEGRAM;
    };
  }
}
