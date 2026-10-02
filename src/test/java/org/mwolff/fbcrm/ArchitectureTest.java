package org.mwolff.fbcrm;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.security.MessageDigest;
import java.util.Arrays;
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
  void sessionTokenCodec_thenComparesSignaturesWithMessageDigest() {
    classes()
        .that()
        .haveSimpleName("SessionTokenCodec")
        .should()
        .callMethod(MessageDigest.class, "isEqual", byte[].class, byte[].class)
        .because("der Signaturvergleich laeuft ohne frueh abbrechenden Vergleich")
        .check(CLASSES);
  }

  @Test
  void authInfrastructure_thenNeverComparesByteArraysWithArraysEquals() {
    noClasses()
        .that()
        .resideInAPackage("..auth.infrastructure..")
        .should()
        .callMethod(Arrays.class, "equals", byte[].class, byte[].class)
        .because(
            "Arrays.equals bricht beim ersten abweichenden Byte ab und verraet damit ueber die"
                + " Laufzeit, wie weit eine geratene Signatur stimmte (CLAUDE-security.md)")
        .allowEmptyShould(true)
        .check(CLASSES);
  }

  @Test
  void angebotModule_thenDoesNotReachIntoRechnung() {
    noClasses()
        .that()
        .resideInAPackage("org.mwolff.fbcrm.angebot..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.mwolff.fbcrm.rechnung..")
        .because(
            "das Angebot kennt seine Rechnungen nur ueber den Port Positionsverwendung; die"
                + " Richtung der Abhaengigkeit ist rechnung -> angebot (Plan #169, E1, E12)")
        .allowEmptyShould(true)
        .check(CLASSES);
  }

  @Test
  void startseiteModule_thenNothingDependsOnIt() {
    noClasses()
        .that()
        .resideOutsideOfPackage("org.mwolff.fbcrm.startseite..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.mwolff.fbcrm.startseite..")
        .because(
            "die Startseite liest aus den Fachmodulen und wird von keinem gelesen; die Richtung"
                + " ist startseite -> angebot, arbeitszeit, rechnung, common (Plan #208, E1)")
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
