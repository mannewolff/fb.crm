package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code .env.example} traegt Platzhalter, keine Geheimnisse.
 *
 * <p>Die Vorlage liegt im Repository und ist damit oeffentlich. Ein echter Wert darin waere ein
 * bekanntes Geheimnis — und weil die Vorlage per {@code cp .env.example .env} zur Konfiguration
 * wird, landete er unveraendert in einer laufenden Instanz.
 *
 * <p>Als Geheimnis gilt, was lang und zufaellig aussieht: mindestens {@value #MIN_LAENGE} Zeichen
 * und eine Zeichen-Entropie von mindestens {@value #MIN_ENTROPIE} Bit. Adressen, Hostnamen und
 * sprechende Platzhalter liegen darunter. Dass die Erkennung einen echten Schluessel ueberhaupt
 * faengt, weisen die beiden Kontrolltests nach — sonst waere eine stumme Pruefung von einer
 * wirksamen nicht zu unterscheiden.
 */
class EnvExampleSecretTest {

  static final int MIN_LAENGE = 24;
  static final double MIN_ENTROPIE = 3.5;

  /** Shannon-Entropie je Zeichen in Bit. */
  static double entropie(final String wert) {
    final Map<Integer, Integer> haeufigkeit = new HashMap<>();
    wert.codePoints().forEach(zeichen -> haeufigkeit.merge(zeichen, 1, Integer::sum));
    final double laenge = wert.codePointCount(0, wert.length());
    double summe = 0;
    for (final int anzahl : haeufigkeit.values()) {
      final double anteil = anzahl / laenge;
      summe -= anteil * (Math.log(anteil) / Math.log(2));
    }
    return summe;
  }

  static boolean siehtAusWieEinGeheimnis(final String wert) {
    return wert.length() >= MIN_LAENGE && entropie(wert) >= MIN_ENTROPIE;
  }

  static List<String> werte() throws IOException {
    return Files.readAllLines(Path.of(".env.example"), StandardCharsets.UTF_8).stream()
        .map(String::strip)
        .filter(zeile -> !zeile.isEmpty() && zeile.charAt(0) != '#' && zeile.contains("="))
        .map(zeile -> zeile.substring(zeile.indexOf('=') + 1).strip())
        .toList();
  }

  @Test
  void envExample_givenItsValues_thenNoneLooksLikeARealSecret() throws IOException {
    // Given
    final List<String> werte = werte();

    // When / Then
    assertThat(werte).isNotEmpty().noneMatch(EnvExampleSecretTest::siehtAusWieEinGeheimnis);
  }

  @Test
  void erkennung_givenARandomBase64Key_thenFlagsIt() {
    // Given — so erzeugt die README ein Session-Secret.
    final byte[] roh = new byte[48];
    new SecureRandom().nextBytes(roh);

    // When / Then
    assertThat(siehtAusWieEinGeheimnis(Base64.getEncoder().encodeToString(roh))).isTrue();
  }

  @Test
  void erkennung_givenARandomHexToken_thenFlagsIt() {
    // Given — so erzeugt die README einen Einmal-Schluessel; Hex hat hoechstens 4 Bit je Zeichen.
    final byte[] roh = new byte[24];
    new SecureRandom().nextBytes(roh);

    // When / Then
    assertThat(siehtAusWieEinGeheimnis(HexFormat.of().formatHex(roh))).isTrue();
  }

  @Test
  void erkennung_givenTypicalPlaceholders_thenLeavesThemAlone() {
    // When / Then
    assertThat(siehtAusWieEinGeheimnis("")).isFalse();
    assertThat(siehtAusWieEinGeheimnis("https://crm.example.org")).isFalse();
    assertThat(siehtAusWieEinGeheimnis("no-reply@fbcrm.local")).isFalse();
    assertThat(siehtAusWieEinGeheimnis("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")).isFalse();
  }
}
