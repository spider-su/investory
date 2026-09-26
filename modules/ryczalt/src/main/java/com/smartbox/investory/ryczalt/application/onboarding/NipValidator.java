package com.smartbox.investory.ryczalt.application.onboarding;

public final class NipValidator {
  private NipValidator() {}

  public static String normalize(String raw) {
    return raw == null ? "" : raw.replaceAll("[^0-9]", "");
  }

  public static boolean isValid(String raw) {
    String nip = normalize(raw);
    if (nip.length() != 10) return false;
    int[] weights = {6, 5, 7, 2, 3, 4, 5, 6, 7};
    int sum = 0;
    for (int i = 0; i < weights.length; i++) sum += (nip.charAt(i) - '0') * weights[i];
    int checksum = sum % 11;
    return checksum != 10 && checksum == nip.charAt(9) - '0';
  }
}
