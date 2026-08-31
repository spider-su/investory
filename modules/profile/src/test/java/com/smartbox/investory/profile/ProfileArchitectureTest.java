package com.smartbox.investory.profile;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Profile Architecture")
class ProfileArchitectureTest {
  static {
    ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(false);
  }

  private static final JavaClasses MAIN =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("com.smartbox.investory.profile", "com.smartbox.investory.longterm.api");

  @DisplayName("profile Does Not Depend On Downstream Modules")
  @Test
  void profileDoesNotDependOnDownstreamModules() {
    noClasses()
        .that()
        .resideInAnyPackage("..profile..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..retirement..", "..integrations..", "..ui..", "..app..")
        .check(MAIN);
  }

  @DisplayName("profile Api Does Not Depend On Application Implementation")
  @Test
  void profileApiDoesNotDependOnApplicationImplementation() {
    noClasses()
        .that()
        .resideInAnyPackage("..profile.api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..profile.application..")
        .check(MAIN);
  }

  @DisplayName("public APIs Do Not Depend On Long-Term Infrastructure")
  @Test
  void publicApisDoNotDependOnLongTermInfrastructure() {
    noClasses()
        .that()
        .resideInAnyPackage("..longterm.api..", "..profile.api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..longterm.infrastructure..")
        .check(MAIN);
  }

  @DisplayName("removed enum aliases are absent")
  @Test
  void removedEnumAliasesAreAbsent() {
    String[] removed = {
      "CashFlowTypeModel",
      "FrequencyModel",
      "InterestTreatmentModel",
      "LongTermAssetTypeModel",
      "ProfileCashFlowType",
      "ProfileFrequency",
      "ProfileInterestTreatment",
      "ProfileAssetType",
      "FundingSource",
      "BucketType",
      "PositionParseState"
    };
    for (String name : removed) {
      assertFalse(
          MAIN.stream().anyMatch(javaClass -> javaClass.getSimpleName().equals(name)),
          () -> "Removed enum alias remains: " + name);
    }
  }

  @DisplayName("public profile records have one canonical constructor")
  @Test
  void publicProfileRecordsHaveOneCanonicalConstructor() {
    assertEquals(1, InvestmentProfile.class.getDeclaredConstructors().length);
    assertEquals(1, ProfileAllocation.class.getDeclaredConstructors().length);
    assertEquals(1, ProjectedLongTermAsset.class.getDeclaredConstructors().length);
    assertEquals(1, ProjectedLongTermAsset.Period.class.getDeclaredConstructors().length);
  }
}
