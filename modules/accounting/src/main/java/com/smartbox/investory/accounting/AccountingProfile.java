package com.smartbox.investory.accounting;

/**
 * POC-wide JDG accounting assumptions.
 *
 * <p>{@code hasUop=true} means the owner has an active UoP whose remuneration satisfies the
 * minimum-remuneration condition for the UoP to be the primary social-insurance title.
 */
public record AccountingProfile(
    boolean hasUop,
    String nip,
    String fullName,
    String taxOfficeCode,
    String email,
    String vatPaymentAccount,
    String ryczaltPaymentAccount,
    String zusPaymentAccount,
    String firstName,
    String surname,
    java.time.LocalDate dateOfBirth,
    String taxMicroAccount) {
  public AccountingProfile(
      boolean hasUop,
      String nip,
      String fullName,
      String taxOfficeCode,
      String email,
      String vatPaymentAccount,
      String ryczaltPaymentAccount,
      String zusPaymentAccount,
      String firstName,
      String surname,
      java.time.LocalDate dateOfBirth) {
    this(
        hasUop,
        nip,
        fullName,
        taxOfficeCode,
        email,
        vatPaymentAccount,
        ryczaltPaymentAccount,
        zusPaymentAccount,
        firstName,
        surname,
        dateOfBirth,
        null);
  }

  public AccountingProfile(
      boolean hasUop,
      String nip,
      String fullName,
      String taxOfficeCode,
      String email,
      String vatPaymentAccount,
      String ryczaltPaymentAccount,
      String zusPaymentAccount) {
    this(
        hasUop,
        nip,
        fullName,
        taxOfficeCode,
        email,
        vatPaymentAccount,
        ryczaltPaymentAccount,
        zusPaymentAccount,
        null,
        null,
        null,
        null);
  }

  public AccountingProfile(boolean hasUop) {
    this(hasUop, null, null, null, null, null, null, null, null, null, null, null);
  }

  public static AccountingProfile defaultProfile() {
    return new AccountingProfile(true);
  }
}
