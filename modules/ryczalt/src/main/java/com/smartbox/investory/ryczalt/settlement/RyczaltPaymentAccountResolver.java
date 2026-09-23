package com.smartbox.investory.ryczalt.settlement;

import com.smartbox.investory.ryczalt.application.port.RyczaltPaymentAccountRulesReader;
import com.smartbox.investory.ryczalt.checker.PaymentAccountRules;
import org.springframework.stereotype.Component;

@Component
public class RyczaltPaymentAccountResolver {
  private final RyczaltPaymentAccountRulesReader reader;

  public RyczaltPaymentAccountResolver(RyczaltPaymentAccountRulesReader reader) {
    this.reader = reader;
  }

  public PaymentAccountRules forProfile(long profileId) {
    return reader.read(profileId);
  }
}
