package org.mwolff.fbcrm.rechnung.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Der Objektspeicher der archivierten Belege gegen eine echte MinIO-Instanz.
 *
 * <p>Gegenstand ist der Port {@code DokumentSpeicher}: ablegen, byteweise wiederlesen — und die
 * beiden Zusagen, die der Adapter darueber hinaus gibt. Erstens die Form des Schluessels, {@code
 * rechnung/<rechnungId>/<uuid>.pdf}: Das {@code rechnung/} trennt die Rechnungen von den Anlagen am
 * Angebot und von kuenftigen Belegarten im selben Eimer. Zweitens, dass ein unbekannter Schluessel
 * scheitert und nicht leere Bytes liefert — ein leeres PDF sahe wie ein gueltiger Beleg aus.
 *
 * <p>Dazu das hochgeladene Original einer nachgetragenen Rechnung (Plan #259, E13): Es liegt unter
 * {@code rechnung-nachtrag/<nachtragId>/<uuid>.pdf}, getrennt von den Belegen, die fb.crm selbst
 * erzeugt, und laesst sich wieder loeschen.
 *
 * <p>Dieser Test deckt die Klasse, die in der {@code pom.xml} von Abdeckung und Mutationstest
 * ausgenommen ist: Sie besteht aus Aufrufen des AWS SDK und ist nur gegen einen echten
 * Objektspeicher sinnvoll pruefbar.
 */
class S3DokumentSpeicherIT extends AbstractIntegrationTest {

  private static final long RECHNUNG = 7L;
  private static final byte[] BELEG = "%PDF-1.7 Rechnung R26-0003".getBytes(UTF_8);
  private static final String SCHLUESSELFORM =
      "rechnung/7/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf";

  private static final long NACHTRAG = 9L;
  private static final byte[] ORIGINAL = "%PDF-1.4 Rechnung RE-2026-001".getBytes(UTF_8);
  private static final String NACHTRAGSFORM =
      "rechnung-nachtrag/9/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf";

  private final S3DokumentSpeicher speicher;

  @Autowired
  S3DokumentSpeicherIT(final S3DokumentSpeicher speicher) {
    this.speicher = speicher;
  }

  @Test
  void lies_afterLege_thenTheDocumentComesBackByteForByte() {
    // Given
    final String schluessel = speicher.lege(RECHNUNG, BELEG);

    // When / Then — Kriterium 14: ausgeliefert wird das abgelegte Dokument, nichts Nachgerechnetes.
    assertThat(speicher.lies(schluessel)).containsExactly(BELEG);
  }

  @Test
  void lege_thenTheKeyCarriesTheRechnungAndTheFileKind() {
    // When / Then — Kennung der Rechnung und Zufallsname unter dem eigenen Praefix.
    assertThat(speicher.lege(RECHNUNG, BELEG)).matches(SCHLUESSELFORM);
  }

  @Test
  void lege_givenTheSameDocumentTwice_thenEachCopyGetsItsOwnKey() {
    // Given
    final String erster = speicher.lege(RECHNUNG, BELEG);

    // When
    final String zweiter = speicher.lege(RECHNUNG, BELEG);

    // Then — eine zweite Ablage zur selben Rechnung ueberschreibt die erste nicht.
    assertThat(zweiter).isNotEqualTo(erster);
  }

  @Test
  void lies_givenAnUnknownKey_thenFailsInsteadOfReturningEmptyBytes() {
    // Given
    final String unbekannt = "rechnung/%d/%s.pdf".formatted(RECHNUNG, UUID.randomUUID());

    // When / Then
    assertThatThrownBy(() -> speicher.lies(unbekannt)).isInstanceOf(NoSuchKeyException.class);
  }

  @Test
  void legeHochgeladenes_thenTheKeyLivesInTheSpaceOfTheNachtrag() {
    // When / Then — E13: eigener Schluesselraum neben den erzeugten Belegen.
    assertThat(speicher.legeHochgeladenes(NACHTRAG, ORIGINAL))
        .startsWith("rechnung-nachtrag/")
        .matches(NACHTRAGSFORM);
  }

  @Test
  void lies_afterLegeHochgeladenes_thenTheOriginalComesBackByteForByte() {
    // Given
    final String schluessel = speicher.legeHochgeladenes(NACHTRAG, ORIGINAL);

    // When / Then
    assertThat(speicher.lies(schluessel)).containsExactly(ORIGINAL);
  }

  @Test
  void loesche_thenTheObjectIsGone() {
    // Given
    final String schluessel = speicher.legeHochgeladenes(NACHTRAG, ORIGINAL);

    // When
    speicher.loesche(schluessel);

    // Then
    assertThatThrownBy(() -> speicher.lies(schluessel)).isInstanceOf(NoSuchKeyException.class);
  }

  @Test
  void loesche_givenAnUnknownKey_thenCompletesWithoutException() {
    // Given — S3 meldet das Loeschen eines unbekannten Schluessels als Erfolg.
    final String unbekannt = "rechnung-nachtrag/%d/%s.pdf".formatted(NACHTRAG, UUID.randomUUID());

    // When / Then
    speicher.loesche(unbekannt);
    assertThatThrownBy(() -> speicher.lies(unbekannt)).isInstanceOf(NoSuchKeyException.class);
  }
}
