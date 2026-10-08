package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@code .env.example} traegt Platzhalter, keine Geheimnisse.
 *
 * <p>Die Vorlage liegt im Repository und ist damit oeffentlich. Ein echter Wert darin waere ein
 * bekanntes Geheimnis — und weil die Vorlage per {@code cp .env.example .env} zur Konfiguration
 * wird, landete er unveraendert in einer laufenden Instanz.
 *
 * <p>Als Geheimnis gilt, was lang und zufaellig aussieht: mindestens {@value #MIN_LAENGE} Zeichen
 * und eine Zeichen-Entropie von mindestens {@value #MIN_ENTROPIE} Bit. Reine Hex-Werte gelten schon
 * ab {@value #MIN_ENTROPIE_HEX} Bit: Ihr Alphabet hat nur 16 Zeichen, und ein echter Schluessel aus
 * {@code openssl rand -hex 24} liegt in etwa einem von hundert Faellen unter 3,5 Bit — die Pruefung
 * liesse genau diesen Anteil durch (Issue #32). Adressen, Hostnamen und sprechende Platzhalter
 * liegen darunter.
 *
 * <p>Dass die Erkennung einen echten Schluessel ueberhaupt faengt, weisen die Kontrolltests nach —
 * sonst waere eine stumme Pruefung von einer wirksamen nicht zu unterscheiden. Sie arbeiten mit
 * festen Werten statt mit Zufall: Ein Test, der mit Zufallsschluesseln prueft, faellt bei jedem
 * hundertsten Lauf, und er faellt dann an der Stelle, an der die Erkennung tatsaechlich luegt.
 */
class EnvExampleSecretTest {

  static final int MIN_LAENGE = 24;
  static final double MIN_ENTROPIE = 3.5;
  static final double MIN_ENTROPIE_HEX = 3.0;

  private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");

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
    if (wert.length() < MIN_LAENGE) {
      return false;
    }
    final double bit = entropie(wert);
    return bit >= MIN_ENTROPIE || (HEX.matcher(wert).matches() && bit >= MIN_ENTROPIE_HEX);
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
  void erkennung_givenABase64Key_thenFlagsIt() {
    // Given — die Form von openssl rand -base64 48 (README): 64 Zeichen, 6 Bit je Zeichen
    final String schluessel = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    // When / Then
    assertThat(siehtAusWieEinGeheimnis(schluessel)).isTrue();
  }

  @Test
  void erkennung_givenAHexToken_thenFlagsIt() {
    // Given — die Form von openssl rand -hex 24 (README): 48 Zeichen, 4 Bit je Zeichen
    final String schluessel = "0123456789abcdef".repeat(3);

    // When / Then
    assertThat(siehtAusWieEinGeheimnis(schluessel)).isTrue();
  }

  @Test
  void erkennung_givenAHexTokenBelowTheGeneralThreshold_thenStillFlagsIt() {
    // Given — 48 Hex-Zeichen mit 3,32 Bit: unter 3,5, wie etwa jeder hundertste echte Schluessel
    final String schluessel = "0123456789".repeat(4) + "01234567";
    assertThat(entropie(schluessel)).isBetween(MIN_ENTROPIE_HEX, MIN_ENTROPIE);

    // When / Then
    assertThat(siehtAusWieEinGeheimnis(schluessel)).isTrue();
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
