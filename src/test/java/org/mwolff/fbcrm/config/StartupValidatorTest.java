package org.mwolff.fbcrm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.slf4j.LoggerFactory;

/**
 * Die Start-Validierung der Betriebsschalter (E27).
 *
 * <p>Sie loest eine Zusage ein, die sonst nur als Kommentar in {@code docker-compose.yml} Z. 36 ff.
 * stuende: Mit {@code FBCRM_DEV_MODE=true} laeuft die Anwendung mit dem voreingestellten
 * Signaturgeheimnis und <b>warnt</b>; mit {@code false} — dem festen Wert des Produktions-Overlays
 * — startet sie gar nicht erst. Ohne diese Pruefung signierte eine Produktionsinstanz ihre
 * Sitzungen mit einem Geheimnis, das im Repository steht.
 *
 * <p>Der Log-Text ist Teil der Zusage: Eine Warnung, die das Geheimnis mitschreibt, traegt es in
 * jedes Log-Archiv und jeden Support-Auszug.
 */
class StartupValidatorTest {

  private static final String EIGENES_GEHEIMNIS = "ein-eigenes-geheimnis-mit-genug-laenge";

  private ListAppender<ILoggingEvent> mitschrift;
  private Logger logger;

  @BeforeEach
  void hoereDemValidatorZu() {
    mitschrift = new ListAppender<>();
    mitschrift.start();
    logger = (Logger) LoggerFactory.getLogger(StartupValidator.class);
    logger.addAppender(mitschrift);
  }

  @AfterEach
  void hoereWiederAuf() {
    logger.detachAppender(mitschrift);
  }

  private static AuthProperties mitGeheimnis(final String geheimnis) {
    return new AuthProperties(
        geheimnis,
        Duration.ofDays(1),
        "fbcrm_session",
        true,
        10,
        Duration.ofMinutes(15),
        List.of());
  }

  private static StartupValidator validator(final boolean devMode, final String geheimnis) {
    return new StartupValidator(mitGeheimnis(geheimnis), new OperationsProperties(devMode));
  }

  private List<String> warnungen() {
    return mitschrift.list.stream()
        .filter(eintrag -> eintrag.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  @Test
  void validate_givenProductionAndTheDevDefaultSecret_thenRefusesToStart() {
    // Given — der feste Wert des Produktions-Overlays trifft auf das Vorgabegeheimnis.
    final StartupValidator validator =
        validator(false, StartupValidator.DEV_DEFAULT_SESSION_SECRET);

    // When / Then
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(validator::validate);
  }

  @Test
  void validate_givenProductionAndTheDevDefaultSecret_thenSaysWhichVariableToSet() {
    // Given
    final StartupValidator validator =
        validator(false, StartupValidator.DEV_DEFAULT_SESSION_SECRET);

    // When / Then — wer den Start abbricht, sagt auch, was zu tun ist.
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(validator::validate)
        .withMessageContaining("FBCRM_SESSION_SECRET");
  }

  @Test
  void validate_givenProductionAndTheDevDefaultSecret_thenNeverNamesTheSecretItself() {
    // Given
    final StartupValidator validator =
        validator(false, StartupValidator.DEV_DEFAULT_SESSION_SECRET);

    // When / Then
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(validator::validate)
        .withMessageNotContaining(StartupValidator.DEV_DEFAULT_SESSION_SECRET);
  }

  @Test
  void validate_givenProductionAndASecretShorterThan32Bytes_thenRefusesToStart() {
    // Given — 31 Zeichen; die Bindung von AuthProperties zaehlt Zeichen, diese Pruefung Bytes.
    final StartupValidator validator = validator(false, "1234567890123456789012345678901");

    // When / Then
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(validator::validate);
  }

  @Test
  void validate_givenProductionAndASecretOfExactly32Bytes_thenStarts() {
    // Given — der Grenzfall auf der Schwelle.
    final StartupValidator validator = validator(false, "12345678901234567890123456789012");

    // When / Then
    assertThat(warnungenNach(validator)).isEmpty();
  }

  @Test
  void validate_givenProductionAndASecretShorterThan32Bytes_thenSaysHowLongItMustBe() {
    // Given
    final StartupValidator validator = validator(false, "1234567890123456789012345678901");

    // When / Then — die Meldung nennt die Schwelle, sonst raet der Betreiber.
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(validator::validate)
        .withMessageContaining(String.valueOf(StartupValidator.MIN_SECRET_BYTES));
  }

  @Test
  void validate_givenProductionAndAProperSecret_thenStaysSilent() {
    // Given
    final StartupValidator validator = validator(false, EIGENES_GEHEIMNIS);

    // When / Then — kein Abbruch und keine Warnung: so soll Produktion aussehen.
    assertThat(warnungenNach(validator)).isEmpty();
  }

  @Test
  void validate_givenDevModeAndTheDevDefaultSecret_thenStartsWithAWarning() {
    // Given — docker-compose.yml Z. 40 ff.: lokal erlaubt, aber nicht stillschweigend.
    final StartupValidator validator = validator(true, StartupValidator.DEV_DEFAULT_SESSION_SECRET);

    // When / Then
    assertThat(warnungenNach(validator)).hasSize(1);
  }

  @Test
  void validate_givenDevModeAndTheDevDefaultSecret_thenTheWarningNamesTheVariableToSet() {
    // Given
    final StartupValidator validator = validator(true, StartupValidator.DEV_DEFAULT_SESSION_SECRET);

    // When / Then
    assertThat(warnungenNach(validator))
        .singleElement()
        .asString()
        .contains("FBCRM_SESSION_SECRET");
  }

  @Test
  void validate_givenDevModeAndTheDevDefaultSecret_thenTheWarningNeverCarriesTheSecret() {
    // Given
    final StartupValidator validator = validator(true, StartupValidator.DEV_DEFAULT_SESSION_SECRET);

    // When / Then — weder ganz noch in Teilen: ein Praefix im Log ist bereits verratenes
    // Schluesselmaterial.
    assertThat(warnungenNach(validator))
        .singleElement()
        .asString()
        .doesNotContain(StartupValidator.DEV_DEFAULT_SESSION_SECRET)
        .doesNotContain(StartupValidator.DEV_DEFAULT_SESSION_SECRET.substring(0, 8));
  }

  @Test
  void validate_givenDevModeAndAnOwnSecret_thenStaysSilent() {
    // Given — wer lokal ein eigenes Geheimnis setzt, braucht keine Ermahnung.
    final StartupValidator validator = validator(true, EIGENES_GEHEIMNIS);

    // When / Then
    assertThat(warnungenNach(validator)).isEmpty();
  }

  @Test
  void validate_givenDevModeAndAShortSecret_thenStartsAnyway() {
    // Given — die Laengenschwelle gilt in Produktion; lokal bleibt der Entwickler Herr seiner
    // Instanz. Abgesichert ist der Fall ohnehin doppelt: AuthProperties bindet ihn gar nicht.
    final StartupValidator validator = validator(true, "zu-kurz");

    // When / Then
    assertThat(warnungenNach(validator)).isEmpty();
  }

  private List<String> warnungenNach(final StartupValidator validator) {
    validator.validate();
    return warnungen();
  }
}
