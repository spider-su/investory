package com.smartbox.investory.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.ui.investment.InProcessInvestmentAssetClient;
import com.smartbox.investory.ui.investment.InProcessInvestmentDashboardClient;
import com.smartbox.investory.ui.investment.InProcessInvestmentReconciliationClient;
import com.smartbox.investory.ui.longterm.InProcessLongTermAssetsClient;
import com.smartbox.investory.ui.profile.InProcessProfileClient;
import com.smartbox.investory.ui.profile.InProcessRetirementProfileClient;
import com.smartbox.investory.ui.retirement.simulation.InProcessRetirementAnalysisClient;
import com.smartbox.investory.ui.retirement.simulation.InProcessRetirementPlanClient;
import com.smartbox.investory.ui.retirement.simulation.InProcessRetirementPlanInputClient;
import com.smartbox.investory.ui.retirement.simulation.InProcessRetirementPresentationClient;
import com.smartbox.investory.ui.retirement.simulation.InProcessRetirementPreviewClient;
import com.smartbox.investory.ui.retirement.simulation.InProcessRetirementProjectionClient;
import com.smartbox.investory.ui.retirement.simulation.InProcessRetirementSandboxPlanClient;
import com.smartbox.investory.ui.retirement.simulation.InProcessRetirementTimelineClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@DisplayName("In-process UI client conventions")
class InProcessClientConventionTest {
  private static final List<Class<?>> CLIENTS =
      List.of(
          InProcessInvestmentAssetClient.class,
          InProcessInvestmentDashboardClient.class,
          InProcessInvestmentReconciliationClient.class,
          InProcessLongTermAssetsClient.class,
          InProcessProfileClient.class,
          InProcessRetirementAnalysisClient.class,
          InProcessRetirementPlanInputClient.class,
          InProcessRetirementPlanClient.class,
          InProcessRetirementSandboxPlanClient.class,
          InProcessRetirementPresentationClient.class,
          InProcessRetirementPreviewClient.class,
          InProcessRetirementProfileClient.class,
          InProcessRetirementProjectionClient.class,
          InProcessRetirementTimelineClient.class);

  @Test
  @DisplayName("clients are components backed by explicitly qualified public APIs")
  void clientsAreComponentsBackedByExplicitlyQualifiedPublicApis() {
    for (Class<?> client : CLIENTS) {
      assertNotNull(client.getAnnotation(Component.class), client.getName());
      var constructors = client.getDeclaredConstructors();
      assertTrue(constructors.length == 1, client.getName());
      var parameters = constructors[0].getParameters();
      assertTrue(parameters.length > 0, client.getName());
      for (var parameter : parameters) {
        assertNotNull(parameter.getAnnotation(Qualifier.class), client.getName());
        assertFalse(parameter.getType().getPackageName().contains(".web"), client.getName());
      }
    }
  }
}
