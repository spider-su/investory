package com.smartbox.investory.integrations.zus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZusTransactionClassifierTest {
  @Test
  void recognizesCanonicalZusNamesAndDiacritics() {
    assertThat(ZusTransactionClassifier.isZusCounterparty("ZUS")).isTrue();
    assertThat(ZusTransactionClassifier.isZusCounterparty("Zakład Ubezpieczeń Społecznych"))
        .isTrue();
    assertThat(ZusTransactionClassifier.isZusCounterparty("ZAKLAD UBEZPIECZEN SPOLECZNYCH"))
        .isTrue();
  }

  @Test
  void rejectsWeakOrUnrelatedNames() {
    assertThat(ZusTransactionClassifier.isZusCounterparty("ALLEGRO")).isFalse();
    assertThat(ZusTransactionClassifier.isZusCounterparty("ZUSY CONSULTING")).isFalse();
    assertThat(ZusTransactionClassifier.isZusCounterparty(null)).isFalse();
  }
}
