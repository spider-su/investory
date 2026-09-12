package com.smartbox.investory.accounting;

/**
 * POC-wide JDG accounting assumptions.
 *
 * <p>{@code hasUop=true} means the owner has an active UoP whose remuneration satisfies the
 * minimum-remuneration condition for the UoP to be the primary social-insurance title.
 */
public record AccountingProfile(boolean hasUop) {
  public static AccountingProfile defaultProfile() {
    return new AccountingProfile(true);
  }
}
