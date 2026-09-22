package com.smartbox.investory.ryczalt.checker;

import com.smartbox.investory.ryczalt.domain.ObligationType;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public record PaymentAccountRules(Map<ObligationType, String> accounts) {
  public static PaymentAccountRules empty() {
    return new PaymentAccountRules(Map.of());
  }

  public PaymentAccountRules {
    EnumMap<ObligationType, String> normalized = new EnumMap<>(ObligationType.class);
    if (accounts != null) {
      accounts.forEach(
          (type, account) -> {
            String value = normalize(account);
            if (type != null && !value.isBlank()) normalized.put(type, value);
          });
    }
    accounts = Map.copyOf(normalized);
  }

  public String accountFor(ObligationType type) {
    return accounts.get(type);
  }

  public boolean isUniqueFor(ObligationType type, String account) {
    String normalized = normalize(account);
    if (normalized.isBlank() || !normalized.equals(accountFor(type))) return false;
    return accounts.entrySet().stream()
        .noneMatch(entry -> entry.getKey() != type && normalized.equals(entry.getValue()));
  }

  public static String normalize(String account) {
    if (account == null) return "";
    String normalized = account.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    if (normalized.startsWith("PL") && normalized.length() == 28) return normalized.substring(2);
    return normalized;
  }
}
