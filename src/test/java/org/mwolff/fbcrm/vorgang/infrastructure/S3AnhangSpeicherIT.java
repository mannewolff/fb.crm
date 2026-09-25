package org.mwolff.fbcrm.vorgang.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.config.MinioProperties;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Der Objektspeicher der Anhaenge gegen eine echte MinIO-Instanz.
 *
 * <p>Gegenstand ist der Port {@code AnhangSpeicher}: ablegen, lesen, die Groesse des abgelegten
 * Objekts — und die beiden Zusagen, die der Adapter darueber hinaus gibt. Erstens die Form des
 * Schluessels aus E9: {@code vorgang/<vorgangId>/<uuid>}, ohne jeden Anteil des Dateinamens; der
 * Name kommt von aussen und waere im Schluessel eine Pfadangabe. Zweitens der fehlende Eimer, den
 * der Adapter beim Start selbst anlegt — ohne ihn beantwortete MinIO jeden Zugriff mit {@code
 * NoSuchBucket} statt mit einer Auskunft.
 */
class S3AnhangSpeicherIT extends AbstractIntegrationTest {

  private static final byte[] INHALT = "Anhang zum Vorgang".getBytes(UTF_8);
  private static final String SCHLUESSELFORM =
      "vorgang/42/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  private final S3AnhangSpeicher speicher;

  @Autowired
  S3AnhangSpeicherIT(final S3AnhangSpeicher speicher) {
    this.speicher = speicher;
  }

  private static InputStream strom(final byte[] inhalt) {
    return new ByteArrayInputStream(inhalt);
  }

  /** Liest die Antwort des Speichers aus und schliesst den Strom — der Aufrufer ist zustaendig. */
  private static byte[] ausgelesen(final Optional<InputStream> antwort) throws IOException {
    try (InputStream offen = antwort.orElseThrow()) {
      return offen.readAllBytes();
    }
  }

  private static MinioProperties zugangMitEimer(final String eimer) {
    return new MinioProperties(
        objektspeicher().getS3URL(),
        objektspeicher().getUserName(),
        objektspeicher().getPassword(),
        eimer);
  }

  @Test
  void lesen_afterAblegen_thenTheContentComesBackByteForByte() throws IOException {
    // Given
    final String schluessel = speicher.ablegen(42L, strom(INHALT), INHALT.length);

    // When / Then
    assertThat(ausgelesen(speicher.lesen(schluessel))).containsExactly(INHALT);
  }

  @Test
  void ablegen_givenASizeSmallerThanTheStream_thenTheObjectHasExactlyThatSize() throws IOException {
    // Given — die Groesse sagt, wie viele Byte zu lesen sind; sie ist keine Schaetzung.
    final String schluessel = speicher.ablegen(42L, strom(INHALT), 6L);

    // When / Then
    assertThat(ausgelesen(speicher.lesen(schluessel)))
        .hasSize(6)
        .containsExactly("Anhang".getBytes(UTF_8));
  }

  @Test
  void lesen_givenAnUnknownKey_thenTellsTheCallerInsteadOfFailing() {
    // When / Then — ein unbekannter Schluessel ist eine Auskunft, kein Fehler des Speichers.
    assertThat(speicher.lesen("vorgang/42/" + UUID.randomUUID())).isEmpty();
  }

  @Test
  void ablegen_thenTheKeyCarriesTheVorgangAndNothingOfTheFile() {
    // When / Then — E9: Vorgangskennung und Zufallsname, kein Anteil eines Dateinamens.
    assertThat(speicher.ablegen(42L, strom(INHALT), INHALT.length)).matches(SCHLUESSELFORM);
  }

  @Test
  void ablegen_givenTheSameContentTwice_thenEachCopyGetsItsOwnKey() {
    // Given
    final String erster = speicher.ablegen(42L, strom(INHALT), INHALT.length);

    // When
    final String zweiter = speicher.ablegen(42L, strom(INHALT), INHALT.length);

    // Then — zwei gleichnamige Dateien am selben Vorgang ueberschreiben einander nicht.
    assertThat(zweiter).isNotEqualTo(erster);
  }

  @Test
  void lesen_givenABucketThatDidNotExistBefore_thenTheStoreAnswersRightAfterStartup() {
    // Given — ein Eimer, den es in dieser MinIO-Instanz noch nicht gibt.
    final S3AnhangSpeicher frisch =
        new S3AnhangSpeicher(zugangMitEimer("frisch-" + UUID.randomUUID()));

    // When / Then — ohne beim Start angelegten Eimer meldete MinIO NoSuchBucket, statt zu
    // antworten, dass es den Schluessel nicht kennt.
    assertThat(frisch.lesen("vorgang/42/" + UUID.randomUUID())).isEmpty();
  }
}
