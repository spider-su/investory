package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import java.time.YearMonth;

public record PeriodRefResponse(YearMonth month, PeriodStatus status) {}
