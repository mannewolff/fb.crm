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
 * <p>Dieser Test deckt die Klasse, die in der {@code pom.xml} von Abdeckung und Mutationstest
 * ausgenommen ist: Sie besteht aus Aufrufen des AWS SDK und ist nur gegen einen echten
 * Objektspeicher sinnvoll pruefbar.
 */
class S3DokumentSpeicherIT extends AbstractIntegrationTest {

  private static final long RECHNUNG = 7L;
  private static final byte[] BELEG = "%PDF-1.7 Rechnung R26-0003".getBytes(UTF_8);
  private static final String SCHLUESSELFORM =
      "rechnung/7/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf";

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
}
