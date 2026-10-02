package com.smartbox.investory.retirement.infrastructure.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.api.model.SavePlanEventCommand;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationEventType;
import com.smartbox.investory.retirement.infrastructure.assumptions.SimulationAssumptionsPersistenceMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CanonicalRetirementPlanServiceClockTest {
  @Test
  void rejectsUnsupportedPersistedBaselineColumnVersion() {
    RetirementPlanRepository plans = mock(RetirementPlanRepository.class);
    RetirementPlanEventRepository events = mock(RetirementPlanEventRepository.class);
    RetirementPlanBaselineCodec codec = new RetirementPlanBaselineCodec(new ObjectMapper());
    RetirementPlanEntity plan = new RetirementPlanEntity();
    plan.setId(4L);
    plan.setPortfolioId(9L);
    plan.setName("versioned");
    SimulationAssumptionsPersistenceMapper.write(
        plan, SimulationAssumptions.defaults(40, 90, 2026));
    plan.setBaselineAsOfYear(2026);
    plan.setBaselineLongTermStateVersion(RetirementPlanBaselineCodec.CURRENT_FORMAT_VERSION + 1);
    plan.setBaselineLongTermState(
        codec.write(com.smartbox.investory.profile.api.model.ProfileAssetProjection.EMPTY));
    when(plans.findByIdAndPortfolioId(4L, 9L)).thenReturn(Optional.of(plan));
    when(events.findAllByPlanIdOrderByYearAscIdAsc(4L)).thenReturn(List.of());
    CanonicalRetirementPlanService service =
        new CanonicalRetirementPlanService(
            plans, events, codec, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    assertThatThrownBy(() -> service.details(9L, 4L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unsupported Long-Term planning baseline version: 2");
  }

  @Test
  void eventMutationUsesInjectedClockAndRequestsLatestPlanByUpdatedAt() {
    Instant fixedInstant = Instant.parse("2030-05-06T07:08:09Z");
    Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
    RetirementPlanRepository plans = mock(RetirementPlanRepository.class);
    RetirementPlanEventRepository events = mock(RetirementPlanEventRepository.class);
    RetirementPlanBaselineCodec codec = new RetirementPlanBaselineCodec(new ObjectMapper());
    RetirementPlanEntity plan = new RetirementPlanEntity();
    plan.setId(4L);
    plan.setPortfolioId(9L);
    plan.setName("clock");
    plan.setArchived(false);
    when(plans.findByIdAndPortfolioId(4L, 9L)).thenReturn(Optional.of(plan));
    when(plans.saveAndFlush(any(RetirementPlanEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(events.save(any(RetirementPlanEventEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(plans.findFirstByPortfolioIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(9L))
        .thenReturn(Optional.of(plan));
    CanonicalRetirementPlanService service =
        new CanonicalRetirementPlanService(plans, events, codec, clock);

    service.savePlanEvent(
        new SavePlanEventCommand(
            9L, 4L, null, 2030, "gift", BigDecimal.ONE, SimulationEventType.ONE_OFF_INCOME, null));
    service.resolvePlanId(9L, null);

    assertThat(plan.getUpdatedAt()).isEqualTo(fixedInstant);
    verify(plans).findFirstByPortfolioIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(9L);
  }
}
