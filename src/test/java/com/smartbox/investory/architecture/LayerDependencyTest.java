package com.smartbox.investory.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class LayerDependencyTest {
  static {
    // Java 26 class files are newer than ArchUnit's bundled ASM; dependency resolution is not
    // needed for these package-direction checks and would pull in JRT classes.
    ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(false);
  }

  private static final JavaClasses MAIN = new ClassFileImporter().importPath("target/classes");

  @Test
  void simulationDoesNotDependOnAccountingRepositories() {
    noClasses()
        .that()
        .resideInAnyPackage("..retirement.simulation..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure.repository..")
        .check(MAIN);
  }

  @Test
  void deterministicSimulationIsPersistenceFree() {
    // SimulationPlanService is the known persistence adapter kept in this package for now.
    noClasses()
        .that()
        .resideInAnyPackage("..retirement.simulation..")
        .and()
        .doNotHaveSimpleName("SimulationPlanService")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..infrastructure.repository..", "..retirement.infrastructure.simulation..")
        .check(MAIN);
  }

  @Test
  void dashboardApplicationDoesNotReachIntoPersistence() {
    noClasses()
        .that()
        .resideInAnyPackage("..application.dashboard..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure.repository..")
        .check(MAIN);
  }

  @Test
  void profileApplicationUsesPortfolioReadBoundary() {
    noClasses()
        .that()
        .resideInAnyPackage("..retirement.profile..")
        .should()
        .dependOnClassesThat()
        .haveSimpleName("PortfolioService")
        .check(MAIN);
  }

  @Test
  void profileApplicationDoesNotDependOnInvestmentRepositories() {
    noClasses()
        .that()
        .resideInAnyPackage("..retirement.profile..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure.repository..")
        .check(MAIN);
  }

  @Test
  void planningApplicationDoesNotDependOnInvestmentRepositories() {
    noClasses()
        .that()
        .resideInAnyPackage("..retirement.planning..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure.repository..")
        .check(MAIN);
  }

  @Test
  void longTermApplicationDoesNotDependOnInvestmentRepositories() {
    noClasses()
        .that()
        .resideInAnyPackage("..longterm.application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure.repository..")
        .check(MAIN);
  }

  @Test
  void retirementApplicationsDoNotDependOnLongTermPersistence() {
    noClasses()
        .that()
        .resideInAnyPackage(
            "..retirement.profile..", "..retirement.planning..", "..retirement.simulation..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..longterm.infrastructure..")
        .check(MAIN);
  }

  @Test
  void longTermApplicationsDoNotDependOnRetirementApplications() {
    noClasses()
        .that()
        .resideInAnyPackage("..longterm.application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..retirement.profile..", "..retirement.planning..", "..retirement.simulation..")
        .check(MAIN);
  }

  @Test
  void investmentDoesNotDependOnRetirement() {
    noClasses()
        .that()
        .resideInAnyPackage("..investment..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..retirement..")
        .check(MAIN);
  }

  @Test
  void longTermDoesNotDependOnRetirement() {
    noClasses()
        .that()
        .resideInAnyPackage("..longterm..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..retirement..")
        .check(MAIN);
  }

  @Test
  void retirementDoesNotDependOnInvestmentOrLongTermImplementations() {
    noClasses()
        .that()
        .resideInAnyPackage("..retirement..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..investment.infrastructure..", "..longterm.infrastructure..")
        .check(MAIN);
  }

  @Test
  void brokerageSnapshotModelsDoNotReachIntoPersistence() {
    noClasses()
        .that()
        .haveNameMatching(
            ".*investment\\.api\\.(BrokeragePositionSnapshot|SharedBrokeragePortfolioSnapshot)")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure.repository..")
        .check(MAIN);
  }

  @Test
  void brokeragePlanningReadContractsDoNotReachIntoPersistence() {
    noClasses()
        .that()
        .haveNameMatching(
            ".*investment\\.api\\.(BrokerageAssetClassification|BrokerageAssetClassificationReader|BrokeragePortfolioContext|BrokeragePortfolioContextReader|HistoricalPortfolioActualsReader|HistoricalPortfolioYear)")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure.repository..")
        .check(MAIN);
  }

  @Test
  void longTermReadContractsDoNotReachIntoPersistence() {
    noClasses()
        .that()
        .resideInAnyPackage("..longterm.api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..longterm.infrastructure..", "..infrastructure.repository..")
        .check(MAIN);
  }

  @Test
  void sharedContractsDoNotDependOnDomainImplementations() {
    noClasses()
        .that()
        .resideInAnyPackage("..shared..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..application..",
            "..services..",
            "..controllers..",
            "..infrastructure.repository..",
            "..longterm.infrastructure..",
            "..retirement.infrastructure.planning..",
            "..retirement.infrastructure.simulation..")
        .check(MAIN);
  }

  @Test
  void accountingServicesDoNotDependOnPlanningOrSimulation() {
    noClasses()
        .that()
        .resideInAnyPackage("..services..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..retirement.planning..", "..retirement.simulation..")
        .check(MAIN);
  }
}
