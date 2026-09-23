package com.smartbox.investory.ryczalt.application.port;

import com.smartbox.investory.ryczalt.checker.PaymentAccountRules;

/** Reads the configured payment accounts for one profile. */
public interface RyczaltPaymentAccountRulesReader {
  PaymentAccountRules read(long profileId);
}
