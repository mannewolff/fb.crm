package org.mwolff.fbcrm;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.security.MessageDigest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.angebot.application.VerbotenerZeitzugriff;

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
  void angebotModule_thenDoesNotReachIntoArbeitszeit() {
    angebotOhneArbeitszeit().check(CLASSES);
  }

  /**
   * Dass die Regel ueberhaupt greift (Issue #228).
   *
   * <p>Gelaufen gegen eine Klassenmenge, die den Verstoss <b>enthaelt</b>: {@code
   * VerbotenerZeitzugriff} liegt im Testbaum, haengt an {@code arbeitszeit} und ist genau das, was
   * die Regel finden soll. Ohne diesen Lauf saehe eine Regel, die nichts finden <em>kann</em>, aus
   * wie eine, die nichts findet — {@code allowEmptyShould(true)} laesst beide gruen durch.
   */
  @Test
  void angebotModule_givenAClassThatReachesIntoArbeitszeit_thenTheRuleFails() {
    final JavaClasses mitVerstoss =
        new ClassFileImporter().importClasses(VerbotenerZeitzugriff.class);

    assertThatThrownBy(() -> angebotOhneArbeitszeit().check(mitVerstoss))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(VerbotenerZeitzugriff.class.getName())
        .hasMessageContaining("arbeitszeit -> angebot")
        .hasMessageContaining("Zeitbindung");
  }

  /*
   * Die Regel als eigene Methode und nicht zweimal geschrieben: Sie laeuft gegen zwei
   * Klassenmengen — den Produktionscode und den Verstoss —, und zwei Abschriften liefen beim
   * ersten Nachziehen des Textes auseinander.
   */
  private static ArchRule angebotOhneArbeitszeit() {
    return noClasses()
        .that()
        .resideInAPackage("org.mwolff.fbcrm.angebot..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.mwolff.fbcrm.arbeitszeit..")
        .because(
            "das Angebot erfaehrt die erfasste Arbeitszeit nur ueber den Port Zeitbindung; die"
                + " Richtung der Abhaengigkeit ist arbeitszeit -> angebot (Plan #218, E6)")
        .allowEmptyShould(true);
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
  void jahresabschlussModule_thenNothingDependsOnIt() {
    noClasses()
        .that()
        .resideOutsideOfPackage("org.mwolff.fbcrm.jahresabschluss..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.mwolff.fbcrm.jahresabschluss..")
        .because(
            "der Jahresabschluss liest aus den Fachmodulen und wird von keinem gelesen; die"
                + " Richtung ist jahresabschluss -> angebot, arbeitszeit, rechnung, common"
                + " (Plan #288, E20)")
        .allowEmptyShould(true)
        .check(CLASSES);
  }

  private static final int HOECHSTENS_PARAMETER = 7;

  /**
   * Keine Entity nimmt jedes Feld einzeln (Plan #238, A5; SonarCloud S107).
   *
   * <p>Die Regel gilt ohne Ausnahme: Issue #241 hat {@code rechnung} und {@code angebot}
   * umgestellt, #242 {@code firma} und {@code eigeneangaben}, #243 {@code mail} und {@code auth} —
   * mit dem letzten Namen entfiel die Ausnahmeliste, die die Regel bis dahin trug, und mit ihr der
   * Lauf, der sie gegen die leere Liste prueft.
   */
  @Test
  void entities_thenNoConstructorTakesMoreThanSevenParameters() {
    classes()
        .that()
        .areAnnotatedWith(Entity.class)
        .should(konstruktorenMitHoechstensSiebenParametern())
        .because(
            "eine Entity bildet sich aus ihrem Domaenenobjekt und nimmt nicht jedes Feld einzeln"
                + " (Plan #238, A5; SonarCloud S107)")
        .check(CLASSES);
  }

  private static ArchCondition<JavaClass> konstruktorenMitHoechstensSiebenParametern() {
    return new ArchCondition<>(
        "hoechstens " + HOECHSTENS_PARAMETER + " Konstruktorparameter haben") {
      @Override
      public void check(final JavaClass klasse, final ConditionEvents ereignisse) {
        for (final JavaConstructor konstruktor : klasse.getConstructors()) {
          final int anzahl = konstruktor.getRawParameterTypes().size();
          final String text = konstruktor.getFullName() + " hat " + anzahl + " Parameter";
          ereignisse.add(
              anzahl > HOECHSTENS_PARAMETER
                  ? SimpleConditionEvent.violated(konstruktor, text)
                  : SimpleConditionEvent.satisfied(konstruktor, text));
        }
      }
    };
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
