package org.mwolff.fbcrm.angebot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Erkennung der Vorschauart an den ersten Bytes des Inhalts (Plan #150, E4).
 *
 * <p>Gegenstand ist die eine Zusage der Klasse: Was erkannt wird, entscheidet allein der Inhalt.
 * Dateiname und die vom Browser gemeldete Art kommen hier nicht vor — sie sind Eingaben von aussen
 * und werden nirgends gelesen.
 */
class VorschauartTest {

  /** Ein Byte-Feld aus ganzen Zahlen; kuerzer und lesbarer als lauter Umwandlungen. */
  private static byte[] inhalt(final int... werte) {
    final byte[] bytes = new byte[werte.length];
    for (int i = 0; i < werte.length; i++) {
      bytes[i] = (byte) werte[i];
    }
    return bytes;
  }

  @Test
  void erkannt_givenAPngSignature_thenPng() {
    // Given
    final byte[] png = inhalt(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D);

    // When
    final Optional<Vorschauart> art = Vorschauart.erkannt(png);

    // Then
    assertThat(art).contains(Vorschauart.PNG);
  }

  @Test
  void erkannt_givenAJpegSignature_thenJpeg() {
    // Given
    final byte[] jpeg = inhalt(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01);

    // When
    final Optional<Vorschauart> art = Vorschauart.erkannt(jpeg);

    // Then
    assertThat(art).contains(Vorschauart.JPEG);
  }

  @Test
  void erkannt_givenAGif87aSignature_thenGif() {
    // Given
    final byte[] gif = inhalt('G', 'I', 'F', '8', '7', 'a', 0x10, 0x00, 0x10, 0x00, 0x00, 0x00);

    // When
    final Optional<Vorschauart> art = Vorschauart.erkannt(gif);

    // Then
    assertThat(art).contains(Vorschauart.GIF);
  }

  @Test
  void erkannt_givenAGif89aSignature_thenGif() {
    // Given
    final byte[] gif = inhalt('G', 'I', 'F', '8', '9', 'a', 0x10, 0x00, 0x10, 0x00, 0x00, 0x00);

    // When
    final Optional<Vorschauart> art = Vorschauart.erkannt(gif);

    // Then
    assertThat(art).contains(Vorschauart.GIF);
  }

  @Test
  void erkannt_givenAWebpSignature_thenWebp() {
    // Given — RIFF, vier Byte Laenge, dann WEBP.
    final byte[] webp = inhalt('R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P');

    // When
    final Optional<Vorschauart> art = Vorschauart.erkannt(webp);

    // Then
    assertThat(art).contains(Vorschauart.WEBP);
  }

  @Test
  void erkannt_givenAPdfSignature_thenPdf() {
    // Given
    final byte[] pdf = inhalt('%', 'P', 'D', 'F', '-', '1', '.', '7', 0x0A, '%', 0xE2, 0xE3);

    // When
    final Optional<Vorschauart> art = Vorschauart.erkannt(pdf);

    // Then
    assertThat(art).contains(Vorschauart.PDF);
  }

  @Test
  void erkannt_givenHtmlContent_thenNothing() {
    // Given — der Fall, der die Erkennung am Namen widerlegt: Die Datei heisst spaeter
    // bericht.pdf, ihr Inhalt ist HTML, und sie bekommt trotzdem keine Vorschauart.
    final byte[] html = inhalt('<', '!', 'D', 'O', 'C', 'T', 'Y', 'P', 'E', ' ', 'h', 't');

    // When
    final Optional<Vorschauart> art = Vorschauart.erkannt(html);

    // Then
    assertThat(art).isEmpty();
  }

  @Test
  void erkannt_givenFewerBytesThanTheLongestSignature_thenNothing() {
    // Given — vier Byte, die laengste Signatur (WebP) braucht zwoelf; hier faengt ein PNG an,
    // ist aber noch nicht vollstaendig.
    final byte[] angefangen = inhalt(0x89, 'P', 'N', 'G');

    // When
    final Optional<Vorschauart> art = Vorschauart.erkannt(angefangen);

    // Then
    assertThat(art).isEmpty();
  }

  @Test
  void erkannt_givenNoBytesAtAll_thenNothing() {
    // When
    final Optional<Vorschauart> art = Vorschauart.erkannt(inhalt());

    // Then
    assertThat(art).isEmpty();
  }

  @Test
  void erkannt_givenRiffWithoutWebp_thenNothing() {
    // Given — eine WAV-Datei faengt ebenso mit RIFF an; erst die Kennung ab Byte acht entscheidet.
    final byte[] wav = inhalt('R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E');

    // When
    final Optional<Vorschauart> art = Vorschauart.erkannt(wav);

    // Then
    assertThat(art).isEmpty();
  }

  @Test
  void erkannt_givenTheWebpMarkerWithoutRiff_thenNothing() {
    // Given — beide Haelften muessen stimmen: Hier steht die Kennung WEBP ab Byte acht, der
    // RIFF-Vorsatz fehlt aber. Ohne die erste Haelfte der Pruefung reichte die Kennung allein.
    final byte[] ohneRiff = inhalt('F', 'A', 'K', 'E', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P');

    // When
    final Optional<Vorschauart> art = Vorschauart.erkannt(ohneRiff);

    // Then
    assertThat(art).isEmpty();
  }

  @Test
  void erkannt_givenRiffAndTooFewBytesForTheMarker_thenNothing() {
    // Given — RIFF steht da, die Kennung ab Byte acht fehlt noch.
    final byte[] halb = inhalt('R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E');

    // When
    final Optional<Vorschauart> art = Vorschauart.erkannt(halb);

    // Then
    assertThat(art).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "PNG, image/png",
    "JPEG, image/jpeg",
    "GIF, image/gif",
    "WEBP, image/webp",
    "PDF, application/pdf"
  })
  void mimeTyp_thenNamesTheTypeTheContentIsDeliveredAs(
      final Vorschauart art, final String erwartet) {
    // When / Then
    assertThat(art.mimeTyp()).isEqualTo(erwartet);
  }

  @Test
  void signaturBytes_thenCoversTheLongestSignature() {
    // Then — so viele Bytes muss der Anwendungsfall vom Anfang des Inhalts lesen.
    assertThat(Vorschauart.SIGNATUR_BYTES).isEqualTo(12);
  }
}
