package com.smartbox.investory.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@DisplayName("Layer Dependency")
class LayerDependencyTest {
  static {
    ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(false);
  }

  private static final JavaClasses MAIN =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("com.smartbox.investory");

  @DisplayName("investment Does Not Depend On Other Business Domains")
  @Test
  void investmentDoesNotDependOnOtherBusinessDomains() {
    noClasses()
        .that()
        .resideInAnyPackage("..investment..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..longterm..", "..profile..", "..retirement..", "..integrations..")
        .check(MAIN);
  }

  @DisplayName("integrations Use Only Investment Api And Ports")
  @Test
  void integrationsUseOnlyInvestmentApiAndPorts() {
    noClasses()
        .that()
        .resideInAnyPackage("..integrations..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment.accounting..",
            "..investment.ledger..",
            "..investment.performance..",
            "..investment.projection..",
            "..investment.operations..",
            "..investment.imports..",
            "..investment.market..",
            "..investment.valuation..",
            "..investment.reporting..",
            "..investment.reconciliation..",
            "..investment.infrastructure..",
            "..investment.web..")
        .check(MAIN);
  }

  @DisplayName("long Term Does Not Depend On Other Business Domains")
  @Test
  void longTermDoesNotDependOnOtherBusinessDomains() {
    noClasses()
        .that()
        .resideInAnyPackage("..longterm..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..investment..", "..profile..", "..retirement..")
        .check(MAIN);
  }

  @DisplayName("profile Uses Only Investment And Long Term Public Boundaries")
  @Test
  void profileUsesOnlyInvestmentAndLongTermPublicBoundaries() {
    noClasses()
        .that()
        .resideInAnyPackage("com.smartbox.investory.profile..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment.accounting..",
            "..investment.ledger..",
            "..investment.performance..",
            "..investment.projection..",
            "..investment.operations..",
            "..investment.imports..",
            "..investment.market..",
            "..investment.valuation..",
            "..investment.reporting..",
            "..investment.reconciliation..",
            "..investment.infrastructure..",
            "..investment.web..",
            "..longterm.application..",
            "..longterm.infrastructure..",
            "..longterm.web..",
            "..retirement..",
            "..integrations..",
            "..ui..",
            "..app..")
        .check(MAIN);
  }

  @DisplayName("retirement Uses Only Investment And Long Term Public Boundaries")
  @Test
  void retirementUsesOnlyInvestmentAndLongTermPublicBoundaries() {
    noClasses()
        .that()
        .resideInAnyPackage("com.smartbox.investory.retirement..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment.accounting..",
            "..investment.ledger..",
            "..investment.performance..",
            "..investment.projection..",
            "..investment.operations..",
            "..investment.imports..",
            "..investment.market..",
            "..investment.valuation..",
            "..investment.reporting..",
            "..investment.reconciliation..",
            "..investment.infrastructure..",
            "..investment.web..",
            "..longterm.application..",
            "..longterm.infrastructure..",
            "..longterm.web..")
        .check(MAIN);
  }

  @DisplayName("shared Does Not Depend On Business Domains")
  @Test
  void sharedDoesNotDependOnBusinessDomains() {
    noClasses()
        .that()
        .resideInAnyPackage("..shared..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment..",
            "..longterm..",
            "..profile..",
            "..retirement..",
            "..integrations..",
            "..app..")
        .check(MAIN);
  }

  @DisplayName("investment Api Does Not Depend On Investment Implementations")
  @Test
  void investmentApiDoesNotDependOnInvestmentImplementations() {
    noClasses()
        .that()
        .resideInAnyPackage("..investment.api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment.accounting..",
            "..investment.ledger..",
            "..investment.performance..",
            "..investment.projection..",
            "..investment.operations..",
            "..investment.imports..",
            "..investment.market..",
            "..investment.valuation..",
            "..investment.reporting..",
            "..investment.reconciliation..",
            "..investment.infrastructure..",
            "..investment.web..")
        .check(MAIN);
  }

  @DisplayName("long Term Api Does Not Depend On Long Term Implementations")
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

  @DisplayName("profile Api Owns Its Published Models")
  @Test
  void profileApiOwnsItsPublishedModels() {
    noClasses()
        .that()
        .resideInAnyPackage("..profile.api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment..",
            "..longterm.application..",
            "..longterm.infrastructure..",
            "..longterm.web..",
            "..retirement..",
            "..integrations..")
        .check(MAIN);
  }

  @DisplayName("investment Api Does Not Contain Spring Implementation Stereotypes")
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

  @DisplayName("long Term Api Does Not Contain Spring Implementation Stereotypes")
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

  @DisplayName("deterministic Simulation Does Not Depend On Persistence Or Domain Implementations")
  @Test
  void deterministicSimulationDoesNotDependOnPersistenceOrDomainImplementations() {
    noClasses()
        .that()
        .resideInAnyPackage("..retirement.simulation..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..retirement.infrastructure..",
            "..investment.accounting..",
            "..investment.ledger..",
            "..investment.performance..",
            "..investment.projection..",
            "..investment.operations..",
            "..investment.imports..",
            "..investment.market..",
            "..investment.valuation..",
            "..investment.reporting..",
            "..investment.reconciliation..",
            "..investment.infrastructure..",
            "..longterm.application..",
            "..longterm.infrastructure..")
        .check(MAIN);
  }

  @DisplayName("dashboard Application Does Not Reach Into Persistence")
  @Test
  void dashboardApplicationDoesNotReachIntoPersistence() {
    noClasses()
        .that()
        .resideInAnyPackage("..investment.reporting.dashboard.application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..investment..persistence..")
        .check(MAIN);
  }

  @DisplayName("investment Ledger Does Not Depend On Higher Level Slices")
  @Test
  void investmentLedgerDoesNotDependOnHigherLevelSlices() {
    noClasses()
        .that()
        .resideInAnyPackage("..investment.ledger..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment.performance..",
            "..investment.projection..",
            "..investment.reporting..",
            "..investment.reconciliation..",
            "..investment.valuation..",
            "..retirement.planning..",
            "..retirement.simulation..")
        .check(MAIN);
  }

  @DisplayName("investment Valuation Does Not Depend On Higher Level Slices")
  @Test
  void investmentValuationDoesNotDependOnHigherLevelSlices() {
    noClasses()
        .that()
        .resideInAnyPackage("..investment.valuation..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..investment.reporting.dashboard..", "..investment.reconciliation..")
        .check(MAIN);
  }

  @DisplayName("web Ui Does Not Reach Into Business Infrastructure")
  @Test
  void webUiDoesNotReachIntoBusinessInfrastructure() {
    noClasses()
        .that()
        .resideInAnyPackage("..ui..")
        .and()
        .haveSimpleNameNotEndingWith("Test")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment.infrastructure..",
            "..longterm.infrastructure..",
            "..retirement.infrastructure..")
        .check(MAIN);
  }

  @DisplayName("web Ui Uses Only Declared Contracts And Presentation Types")
  @Test
  void webUiUsesOnlyDeclaredContractsAndPresentationTypes() {
    classes()
        .that()
        .resideInAnyPackage("..ui..")
        .and()
        .haveSimpleNameNotEndingWith("Test")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            "..ui..",
            "..shared..",
            "..investment.api..",
            "..investment.reconciliation..",
            "..longterm.api..",
            "..profile.api..",
            "..retirement.api..",
            "..integrations.management.api..",
            "java..",
            "javax..",
            "jakarta..",
            "lombok..",
            "org.springframework..",
            "org.thymeleaf..",
            "com.fasterxml..")
        .check(MAIN);
  }

  @DisplayName("retirement Public API Does Not Depend On Implementations")
  @Test
  void retirementPublicApiDoesNotDependOnImplementations() {
    noClasses()
        .that()
        .resideInAnyPackage("..retirement.api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..retirement.planning..", "..retirement.simulation..")
        .check(MAIN);
  }

  @DisplayName("integration Channels Do Not Depend On Each Other")
  @Test
  void integrationChannelsDoNotDependOnEachOther() {
    noClasses()
        .that()
        .resideInAnyPackage("..integrations.telegram..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..integrations.ai.openai..")
        .check(MAIN);
  }

  @DisplayName("web Ui Does Not Depend On Persistence Repositories")
  @Test
  void webUiDoesNotDependOnPersistenceRepositories() {
    noClasses()
        .that()
        .resideInAnyPackage("..ui..")
        .and()
        .haveSimpleNameNotEndingWith("Test")
        .should()
        .dependOnClassesThat()
        .areAnnotatedWith(Repository.class)
        .check(MAIN);
  }

  @DisplayName("web Ui Uses Clients Instead Of Business Rest Controllers")
  @Test
  void webUiUsesClientsInsteadOfBusinessRestControllers() {
    noClasses()
        .that()
        .resideInAnyPackage("..ui..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment.web..", "..longterm.web..", "..profile.web..", "..retirement.web..")
        .check(MAIN);
  }

  @DisplayName("investment Web Ui Uses Only Investment Public Apis")
  @Test
  void investmentWebUiUsesOnlyInvestmentPublicApis() {
    noClasses()
        .that()
        .resideInAnyPackage("..ui.investment..")
        .and()
        .haveSimpleNameNotEndingWith("Test")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..investment.accounting..",
            "..investment.ledger..",
            "..investment.performance..",
            "..investment.projection..",
            "..investment.operations..",
            "..investment.imports..",
            "..investment.market..",
            "..investment.valuation..",
            "..investment.reporting..",
            "..investment.reconciliation..",
            "..investment.infrastructure..")
        .check(MAIN);
  }

  @DisplayName("long Term Web Ui Uses Only Long Term Public Api")
  @Test
  void longTermWebUiUsesOnlyLongTermPublicApi() {
    noClasses()
        .that()
        .resideInAnyPackage("..ui.longterm..")
        .and()
        .haveSimpleNameNotEndingWith("Test")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..longterm.application..", "..longterm.infrastructure..")
        .check(MAIN);
  }

  @DisplayName("in Process Ui Clients Are Explicit Components")
  @Test
  void inProcessUiClientsAreExplicitComponents() {
    classes()
        .that()
        .resideInAnyPackage("..ui..")
        .and()
        .haveSimpleNameStartingWith("InProcess")
        .should()
        .beAnnotatedWith(Component.class)
        .check(MAIN);
  }

  @DisplayName("business Rest Controllers Do Not Depend On Spring Services")
  @Test
  void businessRestControllersDoNotDependOnSpringServices() {
    noClasses()
        .that()
        .resideInAnyPackage(
            "..investment.web..", "..longterm.web..", "..profile.web..", "..retirement.web..")
        .should()
        .dependOnClassesThat()
        .areAnnotatedWith(Service.class)
        .check(MAIN);
  }

  @DisplayName("concrete Web Ui Clients Use In Process Naming")
  @Test
  void concreteWebUiClientsUseInProcessNaming() {
    classes()
        .that()
        .resideInAnyPackage("..ui..")
        .and()
        .haveSimpleNameEndingWith("Client")
        .and()
        .areNotInterfaces()
        .should()
        .haveSimpleNameStartingWith("InProcess")
        .check(MAIN);
  }

  @DisplayName("profile Web Ui Uses Only Public Contracts")
  @Test
  void profileWebUiUsesOnlyPublicContracts() {
    classes()
        .that()
        .resideInAnyPackage("..ui.profile..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            "..ui.profile..",
            "..ui.investment..",
            "..ui.presentation..",
            "..shared..",
            "..investment.api..",
            "..profile.api..",
            "..retirement.api..",
            "java..",
            "javax..",
            "jakarta..",
            "lombok..",
            "org.springframework..",
            "org.thymeleaf..",
            "com.fasterxml..")
        .check(MAIN);
  }
}
