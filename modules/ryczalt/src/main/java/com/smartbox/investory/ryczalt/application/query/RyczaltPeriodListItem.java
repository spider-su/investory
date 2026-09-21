package com.smartbox.investory.ryczalt.application.query;

import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import java.time.YearMonth;

public record RyczaltPeriodListItem(YearMonth month, PeriodStatus status) {}
