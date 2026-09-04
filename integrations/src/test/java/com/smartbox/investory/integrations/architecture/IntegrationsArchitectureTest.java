package com.smartbox.investory.integrations.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Integrations Architecture")
class IntegrationsArchitectureTest {
  static {
    ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(false);
  }

  private static final JavaClasses MAIN =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("com.smartbox.investory.integrations");

  @DisplayName("management Contracts Do Not Depend On Application Or Adapters")
  @Test
  void managementContractsDoNotDependOnApplicationOrAdapters() {
    noClasses()
        .that()
        .resideInAnyPackage("..management.api..", "..management.spi..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..management.application..",
            "..management.persistence..",
            "..management.scheduling..",
            "..market..",
            "..fx..",
            "..export..",
            "..telegram..",
            "..notifications..",
            "..ai..",
            "..health..")
        .check(MAIN);
  }

  @DisplayName("notification Application Does Not Depend On Telegram")
  @Test
  void notificationApplicationDoesNotDependOnTelegram() {
    noClasses()
        .that()
        .resideInAnyPackage("..notifications.application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..telegram..")
        .check(MAIN);
  }

  @DisplayName("scheduler Depends Only On The Handler Boundary")
  @Test
  void schedulerDependsOnlyOnTheHandlerBoundary() {
    noClasses()
        .that()
        .haveSimpleName("IntegrationJobScheduler")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment..", "..telegram..", "..notifications..", "..market..", "..fx..")
        .check(MAIN);
  }

  @DisplayName(
      "provider Implementations Do Not Depend On Management Application Or Other Providers")
  @Test
  void providerImplementationsDoNotDependOnManagementApplicationOrOtherProviders() {
    noClasses()
        .that()
        .resideInAnyPackage("..market.twelvedata..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..management.application..",
            "..management.persistence..",
            "..management.scheduling..",
            "..fx.exchangeratehost..",
            "..export.yahoo..")
        .check(MAIN);

    noClasses()
        .that()
        .resideInAnyPackage("..fx.exchangeratehost..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..management.application..",
            "..management.persistence..",
            "..management.scheduling..",
            "..market.twelvedata..",
            "..market.yahoo..",
            "..export.yahoo..")
        .check(MAIN);
  }

  @DisplayName("production code uses the application time boundary")
  @Test
  void productionCodeUsesApplicationTimeBoundary() {
    noClasses()
        .should()
        .callMethod(LocalDate.class, "now")
        .orShould()
        .callMethod(LocalDateTime.class, "now")
        .orShould()
        .callMethod(ZonedDateTime.class, "now")
        .orShould()
        .callMethod(OffsetDateTime.class, "now")
        .orShould()
        .callMethod(Instant.class, "now")
        .orShould()
        .callMethod(Year.class, "now")
        .check(MAIN);
  }
}
