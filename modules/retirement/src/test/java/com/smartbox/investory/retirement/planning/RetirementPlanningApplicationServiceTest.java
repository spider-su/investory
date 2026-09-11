package com.smartbox.investory.retirement.planning;

import static org.mockito.Mockito.*;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.planning.application.*;
import com.smartbox.investory.retirement.planning.input.PlanEditorInputNormalizer;
import com.smartbox.investory.retirement.planning.presentation.PlanningCurrencyPresentationService;
import com.smartbox.investory.retirement.planning.reconciliation.PlanningReconciliationService;
import com.smartbox.investory.retirement.planning.review.RetirementPlanReviewService;
import com.smartbox.investory.retirement.planning.timeline.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetirementPlanningApplicationServiceTest {
  @Mock PlanningTimelineFacade timeline;
  @Mock PlanningCurrencyPresentationService presentation;
  @Mock AnnualPlanningRolloverService rollover;
  @Mock PlanningReconciliationService reconciliation;
  @Mock RetirementPlanReviewService planReviews;
  @Mock PlanEditorInputNormalizer editorInputNormalizer;
  @Mock InvestmentProfile profile;

  @Test
  void closingHistoricalYearAutomaticallyAdvancesTimeline() {
    var closed = mock(com.smartbox.investory.retirement.api.model.PastPlanningYear.class);
    when(timeline.closeHistoricalDraft(1L, 2025)).thenReturn(closed);
    var service = service();

    service.closeHistoricalDraft(1L, 2025);

    verify(timeline).closeHistoricalDraft(1L, 2025);
    verify(rollover).rollover(1L);
  }

  @Test
  void closingCurrentYearAutomaticallyAdvancesTimeline() {
    var closed = mock(com.smartbox.investory.retirement.api.model.PastPlanningYear.class);
    when(timeline.closeCurrentYear(1L, 2025, profile)).thenReturn(closed);
    var service = service();

    service.closeCurrentYear(1L, 2025, profile);

    verify(timeline).closeCurrentYear(1L, 2025, profile);
    verify(rollover).rollover(1L);
  }

  private RetirementPlanningApplicationService service() {
    return new RetirementPlanningApplicationService(
        timeline, presentation, rollover, reconciliation, planReviews, editorInputNormalizer);
  }
}
