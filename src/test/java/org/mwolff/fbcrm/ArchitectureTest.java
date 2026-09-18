package org.mwolff.fbcrm;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Schichtenregeln nach CLAUDE-java.md §6.1.
 *
 * <p>Bewusst als regulaere JUnit-Tests gegen einen {@link ClassFileImporter} geschrieben, nicht
 * ueber {@code @AnalyzeClasses}/{@code @ArchTest}: Die ArchUnit-Engine wird von Surefire nicht
 * ausgefuehrt, die Regeln liefen dann als "0 Tests" durch (falsches Gruen).
 */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("org.mwolff.fbcrm");

  @Test
  void domainPackages_thenFreeOfSpringAndJpa() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..", "jakarta.persistence..", "jakarta.transaction..")
        .because("das Domaenenmodell ist framework-frei (CLAUDE-java.md §6.1)")
        .allowEmptyShould(true)
        .check(CLASSES);
  }

  @Test
  void webPackages_thenDoNotReachIntoInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("..web..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..infrastructure..")
        .because("Controller delegieren an die Application-Schicht, nicht an Adapter")
        .allowEmptyShould(true)
        .check(CLASSES);
  }

  @Test
  void modules_thenFreeOfCycles() {
    slices()
        .matching("org.mwolff.fbcrm.(*)..")
        .should()
        .beFreeOfCycles()
        .because("zyklische Paketabhaengigkeiten sind verboten (CLAUDE-java.md §6.5)")
        .allowEmptyShould(true)
        .check(CLASSES);
  }
}
