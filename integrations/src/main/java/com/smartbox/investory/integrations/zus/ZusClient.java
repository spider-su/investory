package com.smartbox.investory.integrations.zus;

import java.time.LocalDate;
import java.util.List;

/** Provider-neutral contract for retrieving settled ZUS payments. */
public interface ZusClient {
  List<ZusPayment> findPayments(Long profileId, LocalDate from, LocalDate to);
}
