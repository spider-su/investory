package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.smartbox.investory.retirement.api.model.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Annual Planning Rollover Service")
class AnnualPlanningRolloverServiceTest {
  @Mock PlanningTimelineFacade timeline;

  @DisplayName(
      "exposes Complete Historical Drafts For Explicit Review And Ensures Calendar Current Year")
  @Test
  void exposesCompleteHistoricalDraftsForExplicitReviewAndEnsuresCalendarCurrentYear() {
    when(timeline.historicalYears(1L)).thenReturn(List.of(2025, 2026));
    when(timeline.pastYear(1L, 2025))
        .thenReturn(new PastPlanningYear(2025, PlanningYearStatus.CLOSED, null, null, null));
    when(timeline.pastYear(1L, 2026))
        .thenReturn(new PastPlanningYear(2026, PlanningYearStatus.DRAFT, null, null, null));
    when(timeline.ensureCurrentYear(1L)).thenReturn(true);

    AnnualPlanningRolloverResult result = service().rollover(1L);

    assertEquals(2027, result.currentYear());
    assertEquals(List.of(), result.closedYears());
    assertEquals(List.of(2026), result.pendingHistoricalYears());
    verify(timeline).createHistoricalDraft(1L, 2025);
    verify(timeline).createHistoricalDraft(1L, 2026);
    verify(timeline, never()).closeHistoricalDraft(anyLong(), anyInt());
    verify(timeline).ensureCurrentYear(1L);
  }

  @DisplayName("leaves Incomplete Historical Draft Open And Does Not Close It")
  @Test
  void leavesIncompleteHistoricalDraftOpenAndDoesNotCloseIt() {
    when(timeline.historicalYears(1L)).thenReturn(List.of(2026));
    when(timeline.pastYear(1L, 2026))
        .thenReturn(new PastPlanningYear(2026, PlanningYearStatus.DRAFT, null, null, null));
    AnnualPlanningRolloverResult result = service().rollover(1L);

    assertEquals(List.of(2026), result.pendingHistoricalYears());
    verify(timeline, never()).closeHistoricalDraft(anyLong(), anyInt());
  }

  @DisplayName("already Closed History Is Idempotent")
  @Test
  void alreadyClosedHistoryIsIdempotent() {
    when(timeline.historicalYears(1L)).thenReturn(List.of(2026));
    when(timeline.pastYear(1L, 2026))
        .thenReturn(
            new PastPlanningYear(2026, PlanningYearStatus.CLOSED, Instant.now(), null, null));
    when(timeline.ensureCurrentYear(1L)).thenReturn(false);

    AnnualPlanningRolloverResult result = service().rollover(1L);

    assertEquals(List.of(), result.closedYears());
    assertEquals(false, result.currentYearCreated());
    verify(timeline, never()).historicalCloseStatus(1L, 2026);
    verify(timeline, never()).closeHistoricalDraft(anyLong(), anyInt());
  }

  private AnnualPlanningRolloverService service() {
    return new AnnualPlanningRolloverService(
        timeline, Clock.fixed(Instant.parse("2027-01-15T00:00:00Z"), ZoneOffset.UTC));
  }
}
