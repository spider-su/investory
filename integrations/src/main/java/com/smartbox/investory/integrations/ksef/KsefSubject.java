package com.smartbox.investory.integrations.ksef;

/** KSeF query perspective. The taxpayer is the seller, the buyer, or a third party. */
public enum KsefSubject {
  SELLER("Subject1"),
  BUYER("Subject2"),
  THIRD_PARTY("Subject3");

  private final String code;

  KsefSubject(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
