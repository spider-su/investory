package com.smartbox.investory.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

class LayerDependencyTest {
  static {
    ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(false);
  }

  private static final JavaClasses MAIN = new ClassFileImporter().importPath("target/classes");

  @Test
  void investmentDoesNotDependOnOtherBusinessDomains() {
    noClasses()
        .that()
        .resideInAnyPackage("..investment..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..longterm..", "..retirement..")
        .check(MAIN);
  }

  @Test
  void longTermDoesNotDependOnOtherBusinessDomains() {
    noClasses()
        .that()
        .resideInAnyPackage("..longterm..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..investment..", "..retirement..")
        .check(MAIN);
  }

  @Test
  void retirementUsesOnlyInvestmentAndLongTermPublicBoundaries() {
    noClasses()
        .that()
        .resideInAnyPackage("..retirement..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment.accounting..",
            "..investment.imports..",
            "..investment.market..",
            "..investment.reporting..",
            "..investment.reconciliation..",
            "..investment.infrastructure..",
            "..investment.web..",
            "..longterm.application..",
            "..longterm.infrastructure..",
            "..longterm.web..")
        .check(MAIN);
  }

  @Test
  void sharedDoesNotDependOnBusinessDomains() {
    noClasses()
        .that()
        .resideInAnyPackage("..shared..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..investment..", "..longterm..", "..retirement..")
        .check(MAIN);
  }

  @Test
  void investmentApiDoesNotDependOnInvestmentImplementations() {
    noClasses()
        .that()
        .resideInAnyPackage("..investment.api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment.accounting..",
            "..investment.imports..",
            "..investment.market..",
            "..investment.reporting..",
            "..investment.reconciliation..",
            "..investment.infrastructure..",
            "..investment.web..")
        .check(MAIN);
  }

  @Test
  void longTermApiDoesNotDependOnLongTermImplementations() {
    noClasses()
        .that()
        .resideInAnyPackage("..longterm.api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..longterm.application..", "..longterm.infrastructure..", "..longterm.web..")
        .check(MAIN);
  }

  @Test
  void investmentApiDoesNotContainSpringImplementationStereotypes() {
    noClasses()
        .that()
        .resideInAnyPackage("..investment.api..")
        .should()
        .beAnnotatedWith(Service.class)
        .check(MAIN);
    noClasses()
        .that()
        .resideInAnyPackage("..investment.api..")
        .should()
        .beAnnotatedWith(Component.class)
        .check(MAIN);
    noClasses()
        .that()
        .resideInAnyPackage("..investment.api..")
        .should()
        .beAnnotatedWith(Repository.class)
        .check(MAIN);
  }

  @Test
  void longTermApiDoesNotContainSpringImplementationStereotypes() {
    noClasses()
        .that()
        .resideInAnyPackage("..longterm.api..")
        .should()
        .beAnnotatedWith(Service.class)
        .check(MAIN);
    noClasses()
        .that()
        .resideInAnyPackage("..longterm.api..")
        .should()
        .beAnnotatedWith(Component.class)
        .check(MAIN);
    noClasses()
        .that()
        .resideInAnyPackage("..longterm.api..")
        .should()
        .beAnnotatedWith(Repository.class)
        .check(MAIN);
  }

  @Test
  void deterministicSimulationDoesNotDependOnPersistenceOrDomainImplementations() {
    // SimulationPlanService is the persistence-owning orchestration adapter in this package.
    noClasses()
        .that()
        .resideInAnyPackage("..retirement.simulation..")
        .and()
        .doNotHaveFullyQualifiedName(
            "com.smartbox.investory.retirement.simulation.SimulationPlanService")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..retirement.infrastructure..",
            "..investment.accounting..",
            "..investment.imports..",
            "..investment.market..",
            "..investment.reporting..",
            "..investment.reconciliation..",
            "..investment.infrastructure..",
            "..longterm.application..",
            "..longterm.infrastructure..")
        .check(MAIN);
  }

  @Test
  void dashboardApplicationDoesNotReachIntoPersistence() {
    noClasses()
        .that()
        .resideInAnyPackage("..investment.reporting.dashboard.application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..investment.infrastructure.persistence..")
        .check(MAIN);
  }

  @Test
  void accountingDoesNotDependOnRetirementPlanningOrSimulation() {
    noClasses()
        .that()
        .resideInAnyPackage("..investment.accounting..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..retirement.planning..", "..retirement.simulation..")
        .check(MAIN);
  }
}
