package com.smartbox.investory.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.annotation.Annotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@DisplayName("Layer Dependency")
class LayerDependencyTest {
  private static final String ROOT = "com.smartbox.investory";
  private static final String INVESTMENT = ROOT + ".investment..";
  private static final String LONG_TERM = ROOT + ".longterm..";
  private static final String PROFILE = ROOT + ".profile..";
  private static final String RETIREMENT = ROOT + ".retirement..";
  private static final String INTEGRATIONS = ROOT + ".integrations..";
  private static final String SHARED = ROOT + ".shared..";
  private static final String UI = ROOT + ".ui..";

  private static final String INVESTMENT_API = ROOT + ".investment.api..";
  private static final String LONG_TERM_API = ROOT + ".longterm.api..";
  private static final String PROFILE_API = ROOT + ".profile.api..";
  private static final String RETIREMENT_API = ROOT + ".retirement.api..";

  private static final String[] INVESTMENT_IMPLEMENTATIONS = {
    ROOT + ".investment.accounting..",
    ROOT + ".investment.ledger..",
    ROOT + ".investment.performance..",
    ROOT + ".investment.projection..",
    ROOT + ".investment.operations..",
    ROOT + ".investment.imports..",
    ROOT + ".investment.market..",
    ROOT + ".investment.valuation..",
    ROOT + ".investment.reporting..",
    ROOT + ".investment.reconciliation..",
    ROOT + ".investment.infrastructure..",
    ROOT + ".investment.web.."
  };

  private static final String[] LONG_TERM_IMPLEMENTATIONS = {
    ROOT + ".longterm.application..", ROOT + ".longterm.infrastructure..", ROOT + ".longterm.web.."
  };

  private static final String[] BUSINESS_INFRASTRUCTURE = {
    ROOT + ".investment.infrastructure..",
    ROOT + ".longterm.infrastructure..",
    ROOT + ".retirement.infrastructure.."
  };

  private static final String[] UI_HIDDEN_ADAPTERS = {
    ROOT + ".investment.ledger..persistence..",
    ROOT + ".investment.valuation..persistence..",
    ROOT + ".investment.infrastructure.persistence..",
    ROOT + ".retirement.rest..",
    ROOT + ".ryczalt.persistence.."
  };

  private static final JavaClasses MAIN =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages(ROOT);

  static {
    ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(false);
  }

  @Test
  @DisplayName("business domains do not cross their declared boundaries")
  void businessDomainsDoNotCrossTheirDeclaredBoundaries() {
    forbid(INVESTMENT, ROOT + ".longterm..", PROFILE, RETIREMENT, INTEGRATIONS);
    forbid(LONG_TERM, INVESTMENT, PROFILE, RETIREMENT);
  }

  @Test
  @DisplayName("integrations use only investment public and integration contracts")
  void integrationsUseOnlyInvestmentPublicAndIntegrationContracts() {
    forbid(INTEGRATIONS, INVESTMENT_IMPLEMENTATIONS);
  }

  @Test
  @DisplayName("profile and retirement use published boundaries")
  void profileAndRetirementUsePublishedBoundaries() {
    forbid(
        PROFILE,
        concat(
            INVESTMENT_IMPLEMENTATIONS,
            LONG_TERM_IMPLEMENTATIONS,
            RETIREMENT,
            INTEGRATIONS,
            UI,
            ROOT + ".app.."));
    forbid(RETIREMENT, concat(INVESTMENT_IMPLEMENTATIONS, LONG_TERM_IMPLEMENTATIONS));
  }

  @Test
  @DisplayName("profile and retirement do not inspect long-term taxonomy")
  void profileAndRetirementDoNotInspectLongTermTaxonomy() {
    noClasses()
        .that()
        .resideInAnyPackage(PROFILE, RETIREMENT)
        .should()
        .dependOnClassesThat()
        .haveSimpleName("LongTermAssetType")
        .check(MAIN);
  }

  @Test
  @DisplayName("shared does not depend on business domains")
  void sharedDoesNotDependOnBusinessDomains() {
    forbid(SHARED, INVESTMENT, LONG_TERM, PROFILE, RETIREMENT, INTEGRATIONS, ROOT + ".app..");
  }

  @Test
  @DisplayName("public APIs do not depend on implementations or Spring stereotypes")
  void publicApisDoNotDependOnImplementationsOrSpringStereotypes() {
    forbid(INVESTMENT_API, INVESTMENT_IMPLEMENTATIONS);
    forbid(LONG_TERM_API, LONG_TERM_IMPLEMENTATIONS);
    forbid(RETIREMENT_API, ROOT + ".retirement.planning..", ROOT + ".retirement.simulation..");

    for (String api : new String[] {INVESTMENT_API, LONG_TERM_API}) {
      forbidAnnotated(api, Service.class);
      forbidAnnotated(api, Component.class);
      forbidAnnotated(api, Repository.class);
    }
  }

  @Test
  @DisplayName("long-term application and web layers do not point inward")
  void longTermApplicationAndWebLayersDoNotPointInward() {
    forbid(
        ROOT + ".longterm.web..",
        ROOT + ".longterm.application..",
        ROOT + ".longterm.infrastructure..");
    forbid(ROOT + ".longterm.application..", ROOT + ".longterm.web..");
    noClasses()
        .that()
        .resideInAnyPackage(ROOT + ".ui.longterm..")
        .and()
        .haveSimpleNameNotStartingWith("InProcess")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            ROOT + ".longterm.application..",
            ROOT + ".longterm.infrastructure..",
            ROOT + ".longterm.web..")
        .check(MAIN);
  }

  @Test
  @DisplayName("deterministic simulation does not depend on adapters")
  void deterministicSimulationDoesNotDependOnAdapters() {
    noClasses()
        .that()
        .resideInAnyPackage(ROOT + ".retirement.simulation..")
        .and()
        .haveSimpleNameNotStartingWith("InProcess")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            ROOT + ".retirement.infrastructure..",
            ROOT + ".retirement.planning.application..",
            ROOT + ".retirement.preview..",
            ROOT + ".longterm.application..",
            ROOT + ".longterm.infrastructure..")
        .check(MAIN);
  }

  @Test
  @DisplayName("selected lower investment layers do not depend on higher layers")
  void selectedLowerInvestmentLayersDoNotDependOnHigherLayers() {
    forbid(
        ROOT + ".investment.ledger..",
        ROOT + ".investment.performance..",
        ROOT + ".investment.projection..",
        ROOT + ".investment.reporting..",
        ROOT + ".investment.reconciliation..",
        ROOT + ".investment.valuation..",
        ROOT + ".retirement.planning..",
        ROOT + ".retirement.simulation..");
    forbid(
        ROOT + ".investment.valuation..",
        ROOT + ".investment.reporting.dashboard..",
        ROOT + ".investment.reconciliation..");
  }

  @Test
  @DisplayName("dashboard application does not reach persistence")
  void dashboardApplicationDoesNotReachPersistence() {
    forbid(
        ROOT + ".investment.reporting.dashboard.application..",
        ROOT + ".investment..persistence..");
  }

  @Test
  @DisplayName("web UI uses declared contracts and presentation types")
  void webUiUsesDeclaredContractsAndPresentationTypes() {
    noClasses()
        .that()
        .resideInAnyPackage(UI)
        .and()
        .haveSimpleNameNotStartingWith("InProcess")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(concat(BUSINESS_INFRASTRUCTURE, UI_HIDDEN_ADAPTERS))
        .check(MAIN);

    classes()
        .that()
        .resideInAnyPackage(UI)
        .and()
        .haveSimpleNameNotEndingWith("Test")
        .and()
        .haveSimpleNameNotStartingWith("InProcess")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            UI,
            SHARED,
            INVESTMENT_API,
            ROOT + ".investment.reconciliation..",
            LONG_TERM_API,
            PROFILE_API,
            RETIREMENT_API,
            ROOT + ".retirement.api.contract..",
            ROOT + ".integrations.management.api..",
            ROOT + ".accounting.api..",
            ROOT + ".ryczalt..",
            "java..",
            "javax..",
            "jakarta..",
            "lombok..",
            "org.springframework..",
            "org.thymeleaf..",
            "com.fasterxml..")
        .check(MAIN);
  }

  @Test
  @DisplayName("business REST controllers do not depend on services or adapters")
  void businessRestControllersDoNotDependOnServicesOrAdapters() {
    String[] controllers = {
      ROOT + ".investment.web..",
      ROOT + ".longterm.web..",
      ROOT + ".profile.web..",
      ROOT + ".retirement.rest.."
    };
    for (String controller : controllers) {
      forbidAnnotated(controller, Service.class);
    }
    forbid(
        ROOT + ".retirement.rest..",
        ROOT + ".retirement.infrastructure..",
        ROOT + ".retirement.simulation..");
    forbid(
        ROOT + ".retirement.analysis..",
        ROOT + ".retirement.rest..",
        ROOT + ".retirement.infrastructure..");
  }

  @Test
  @DisplayName("integration channels remain independent")
  void integrationChannelsRemainIndependent() {
    forbid(ROOT + ".integrations.telegram..", ROOT + ".integrations.ai.openai..");
  }

  @Test
  @DisplayName("web UI uses explicit in-process clients and constructor injection")
  void webUiUsesExplicitInProcessClientsAndConstructorInjection() {
    classes()
        .that()
        .resideInAnyPackage(UI)
        .and()
        .haveSimpleNameStartingWith("InProcess")
        .should()
        .beAnnotatedWith(Component.class)
        .check(MAIN);

    classes()
        .that()
        .resideInAnyPackage(UI)
        .and()
        .haveSimpleNameEndingWith("Client")
        .and()
        .areNotInterfaces()
        .should()
        .haveSimpleNameStartingWith("InProcess")
        .orShould()
        .haveSimpleNameStartingWith("Http")
        .check(MAIN);

    noFields()
        .that()
        .areDeclaredInClassesThat()
        .resideInAnyPackage(UI)
        .should()
        .beAnnotatedWith(Autowired.class)
        .check(MAIN);
  }

  private static void forbid(String source, String... targets) {
    noClasses()
        .that()
        .resideInAnyPackage(source)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(targets)
        .check(MAIN);
  }

  private static void forbidAnnotated(String source, Class<? extends Annotation> annotation) {
    noClasses()
        .that()
        .resideInAnyPackage(source)
        .should()
        .dependOnClassesThat()
        .areAnnotatedWith(annotation)
        .check(MAIN);
  }

  private static String[] concat(String[] first, String[] second, String... rest) {
    String[] result = new String[first.length + second.length + rest.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    System.arraycopy(rest, 0, result, first.length + second.length, rest.length);
    return result;
  }
}
