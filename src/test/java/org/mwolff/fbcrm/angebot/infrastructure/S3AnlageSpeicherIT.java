package org.mwolff.fbcrm.angebot.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Der Objektspeicher der Anlagen am Angebot gegen eine echte MinIO-Instanz.
 *
 * <p>Gegenstand ist der Port {@code AnlageSpeicher}: ablegen, byteweise wiederlesen, loeschen — und
 * die Zusagen, die der Adapter darueber hinaus gibt. Die Form des Schluessels aus E3, {@code
 * angebot/<angebotId>/anlage/<uuid>}: Das {@code anlage/} trennt die Anlagen von den archivierten
 * Belegen desselben Angebots, und der Schluessel traegt keinen Teil des Dateinamens — ein Name von
 * aussen waere dort eine Pfadangabe. Dazu die beiden Faelle, in denen ein unbekannter Schluessel
 * kein Fehler ist: {@code lesen} liefert leer, {@code loeschen} laeuft durch.
 *
 * <p>Dieser Test deckt die Klasse, die in der {@code pom.xml} von Abdeckung und Mutationstest
 * ausgenommen ist: Sie besteht aus Aufrufen des AWS SDK und ist nur gegen einen echten
 * Objektspeicher sinnvoll pruefbar.
 */
class S3AnlageSpeicherIT extends AbstractIntegrationTest {

  private static final long ANGEBOT = 11L;
  private static final byte[] INHALT = "%PDF-1.7 Leistungsbeschreibung".getBytes(UTF_8);
  private static final String DATEINAME = "leistungsbeschreibung.pdf";
  private static final String SCHLUESSELFORM =
      "angebot/11/anlage/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  private final S3AnlageSpeicher speicher;

  @Autowired
  S3AnlageSpeicherIT(final S3AnlageSpeicher speicher) {
    this.speicher = speicher;
  }

  @Test
  void lesen_afterAblegen_thenTheContentComesBackByteForByte() throws IOException {
    // Given
    final String schluessel = ablege(INHALT);

    // When
    final Optional<InputStream> gelesen = speicher.lesen(schluessel);

    // Then — ausgeliefert wird das Abgelegte, nichts Nachgerechnetes.
    assertThat(gelesen).isPresent();
    try (InputStream strom = gelesen.orElseThrow()) {
      assertThat(strom.readAllBytes()).containsExactly(INHALT);
    }
  }

  @Test
  void ablegen_thenTheKeyCarriesTheAngebotAndNoPartOfTheFileName() {
    // When
    final String schluessel = ablege(INHALT);

    // Then — E3: Praefix, Kennung, Zufallsname; der Dateiname bleibt in der Datenbank.
    assertThat(schluessel).matches(SCHLUESSELFORM).doesNotContain(DATEINAME, "leistung", ".pdf");
  }

  @Test
  void ablegen_givenTwoUploadsOnTheSameAngebot_thenEachGetsItsOwnKey() {
    // Given
    final String erster = ablege(INHALT);

    // When
    final String zweiter = ablege(INHALT);

    // Then — zweimal dieselbe Datei am selben Angebot sind zwei Anlagen.
    assertThat(zweiter).isNotEqualTo(erster);
  }

  @Test
  void lesen_givenAnUnknownKey_thenEmpty() {
    // When / Then — der Aufrufer soll 404 melden koennen, nicht scheitern.
    assertThat(speicher.lesen(unbekannterSchluessel())).isEmpty();
  }

  @Test
  void lesen_afterLoeschen_thenEmpty() {
    // Given
    final String schluessel = ablege(INHALT);

    // When
    speicher.loeschen(schluessel);

    // Then
    assertThat(speicher.lesen(schluessel)).isEmpty();
  }

  @Test
  void loeschen_givenAnUnknownKey_thenDoesNotThrow() {
    // When / Then — das Loeschen laeuft erst nach dem Commit und darf nicht daran scheitern, dass
    // das Objekt nie entstand oder schon fort ist (E9).
    assertThatCode(() -> speicher.loeschen(unbekannterSchluessel())).doesNotThrowAnyException();
  }

  private String ablege(final byte[] inhalt) {
    return speicher.ablegen(ANGEBOT, new ByteArrayInputStream(inhalt), inhalt.length);
  }

  private static String unbekannterSchluessel() {
    return "angebot/%d/anlage/%s".formatted(ANGEBOT, UUID.randomUUID());
  }
}
