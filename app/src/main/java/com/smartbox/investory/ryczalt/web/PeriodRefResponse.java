package com.smartbox.investory.ryczalt.web;

import java.time.YearMonth;

public record PeriodRefResponse(YearMonth month, AccountingPeriodLifecycle status) {}
