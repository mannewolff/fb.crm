package org.mwolff.fbcrm.rechnung.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicherAusfall;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Der Objektspeicher des Briefpapiers gegen eine echte MinIO-Instanz (Plan #363, E6).
 *
 * <p>Gegenstand ist der Port {@code Briefpapierspeicher}: ablegen, byteweise wiederlesen, loeschen
 * — und seine beiden Zusagen. Erstens die Form des Schluessels, {@code briefpapier/<uuid>.pdf}, mit
 * einem eigenen Zufallsnamen je Ablage, damit ein ersetztes Briefpapier das vorige nicht
 * ueberschreibt. Zweitens, dass das Loeschen eines unbekannten Schluessels nicht scheitert: Darauf
 * stuetzt sich das Entfernen des alten Objekts nach dem Commit.
 */
class S3BriefpapierspeicherIT extends AbstractIntegrationTest {

  private static final byte[] BRIEFPAPIER = "%PDF-1.7 Briefpapier".getBytes(UTF_8);
  private static final String SCHLUESSELFORM =
      "briefpapier/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf";

  private final S3Briefpapierspeicher speicher;

  @Autowired
  S3BriefpapierspeicherIT(final S3Briefpapierspeicher speicher) {
    this.speicher = speicher;
  }

  @Test
  void lies_afterLege_thenTheBriefpapierComesBackByteForByte() {
    // Given
    final String schluessel = speicher.lege(BRIEFPAPIER);

    // When / Then
    assertThat(speicher.lies(schluessel)).containsExactly(BRIEFPAPIER);
  }

  @Test
  void lege_thenTheKeyLivesInTheSpaceOfTheBriefpapier() {
    // When / Then — eigener Schluesselraum neben Belegen und nachgetragenen Originalen.
    assertThat(speicher.lege(BRIEFPAPIER))
        .startsWith("briefpapier/")
        .endsWith(".pdf")
        .matches(SCHLUESSELFORM);
  }

  @Test
  void lege_givenTheSameContentTwice_thenEachCopyGetsItsOwnKey() {
    // Given
    final String erster = speicher.lege(BRIEFPAPIER);

    // When
    final String zweiter = speicher.lege(BRIEFPAPIER);

    // Then — ein ersetztes Briefpapier ueberschreibt das vorige nicht.
    assertThat(zweiter).isNotEqualTo(erster);
    assertThat(speicher.lies(erster)).containsExactly(BRIEFPAPIER);
  }

  @Test
  void loesche_thenTheObjectIsGone() {
    // Given
    final String schluessel = speicher.lege(BRIEFPAPIER);

    // When
    speicher.loesche(schluessel);

    // Then — ein fehlendes Objekt kommt als Ausfall heraus, nicht als SDK-Ausnahme.
    assertThatThrownBy(() -> speicher.lies(schluessel)).isInstanceOf(DokumentSpeicherAusfall.class);
  }

  @Test
  void loesche_givenAnUnknownKey_thenCompletesWithoutException() {
    // Given — S3 meldet das Loeschen eines unbekannten Schluessels als Erfolg.
    final String unbekannt = "briefpapier/%s.pdf".formatted(UUID.randomUUID());

    // When / Then
    speicher.loesche(unbekannt);
    assertThatThrownBy(() -> speicher.lies(unbekannt)).isInstanceOf(DokumentSpeicherAusfall.class);
  }
}
