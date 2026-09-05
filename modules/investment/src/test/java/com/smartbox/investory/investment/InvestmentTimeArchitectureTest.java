package com.smartbox.investory.investment;

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

@DisplayName("Investment Time Architecture")
class InvestmentTimeArchitectureTest {
  static {
    ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(false);
  }

  private static final JavaClasses MAIN =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("com.smartbox.investory.investment");

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
