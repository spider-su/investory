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
        .resideInAnyPackage("..application.simulation..")
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
        .resideInAnyPackage("..application.simulation..")
        .and()
        .doNotHaveSimpleName("SimulationPlanService")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure.repository..", "..infrastructure.simulation..")
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
        .resideInAnyPackage("..application.profile..")
        .should()
        .dependOnClassesThat()
        .haveSimpleName("PortfolioService")
        .check(MAIN);
  }

  @Test
  void brokerageSnapshotModelsDoNotReachIntoPersistence() {
    noClasses()
        .that()
        .haveNameMatching(
            ".*services\\.portfolio\\.read\\.(BrokeragePositionSnapshot|SharedBrokeragePortfolioSnapshot)")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure.repository..")
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
            "..infrastructure.longterm..",
            "..infrastructure.planning..",
            "..infrastructure.simulation..")
        .check(MAIN);
  }

  @Test
  void accountingServicesDoNotDependOnPlanningOrSimulation() {
    // PlanningPresentation is the exact cross-domain presentation bridge documented in
    // docs/architecture/overview.md. Stage 5 removes it; no package-wide exception is allowed.
    noClasses()
        .that()
        .resideInAnyPackage("..services..")
        .and()
        .haveNameNotMatching(".*PlanningPresentation.*")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..application.planning..", "..application.simulation..")
        .check(MAIN);
  }
}
