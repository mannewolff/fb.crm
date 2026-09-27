package org.mwolff.fbcrm.angebot.infrastructure;

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
 * beiden Zusagen, die der Adapter darueber hinaus gibt. Erstens die Form des Schluessels aus E9,
 * {@code angebot/<angebotId>/<uuid>.pdf}: Das {@code angebot/} trennt die Belege von den Anhaengen
 * des Vorgangs, die daneben im selben Eimer liegen. Zweitens, dass ein unbekannter Schluessel
 * scheitert und nicht leere Bytes liefert — ein leeres PDF sahe wie ein gueltiger Beleg aus.
 *
 * <p>Dieser Test deckt die Klasse, die in der {@code pom.xml} von Abdeckung und Mutationstest
 * ausgenommen ist: Sie besteht aus Aufrufen des AWS SDK und ist nur gegen einen echten
 * Objektspeicher sinnvoll pruefbar.
 */
class S3DokumentSpeicherIT extends AbstractIntegrationTest {

  private static final long ANGEBOT = 7L;
  private static final byte[] BELEG = "%PDF-1.7 Angebot A-2026-001".getBytes(UTF_8);
  private static final String SCHLUESSELFORM =
      "angebot/7/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf";

  private final S3DokumentSpeicher speicher;

  @Autowired
  S3DokumentSpeicherIT(final S3DokumentSpeicher speicher) {
    this.speicher = speicher;
  }

  @Test
  void lies_afterLege_thenTheDocumentComesBackByteForByte() {
    // Given
    final String schluessel = speicher.lege(ANGEBOT, BELEG);

    // When / Then — Kriterium 14: ausgeliefert wird das abgelegte Dokument, nichts Nachgerechnetes.
    assertThat(speicher.lies(schluessel)).containsExactly(BELEG);
  }

  @Test
  void lege_thenTheKeyCarriesTheAngebotAndTheFileKind() {
    // When / Then — E9: Kennung des Angebots und Zufallsname unter dem eigenen Praefix.
    assertThat(speicher.lege(ANGEBOT, BELEG)).matches(SCHLUESSELFORM);
  }

  @Test
  void lege_givenTheSameDocumentTwice_thenEachCopyGetsItsOwnKey() {
    // Given
    final String erster = speicher.lege(ANGEBOT, BELEG);

    // When
    final String zweiter = speicher.lege(ANGEBOT, BELEG);

    // Then — ein zweiter Versand am selben Angebot ueberschreibt den ersten Beleg nicht.
    assertThat(zweiter).isNotEqualTo(erster);
  }

  @Test
  void lies_givenAnUnknownKey_thenFailsInsteadOfReturningEmptyBytes() {
    // Given
    final String unbekannt = "angebot/%d/%s.pdf".formatted(ANGEBOT, UUID.randomUUID());

    // When / Then
    assertThatThrownBy(() -> speicher.lies(unbekannt)).isInstanceOf(NoSuchKeyException.class);
  }
}
