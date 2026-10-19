package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;

/**
 * Wie Betrag, Zahl und zusammengesetzte Zeilen auf dem Beleg geschrieben werden (Plan #363, E7).
 *
 * <p>Jedes Layout schreibt sie gleich; darum stehen sie an einer Stelle. <b>Was fehlt, hinterlaesst
 * keine Luecke:</b> Eine Zeile aus freiwilligen Angaben besteht nur aus den gefuellten, und ist
 * keine gefuellt, entsteht keine Zeile — {@code null} statt einer Zeile aus Trennern.
 */
final class Belegschreibweisen {

  private Belegschreibweisen() {}

  /**
   * Ein Betrag mit Tausenderpunkt, zwei Nachkommastellen und Eurozeichen: „1.200,00 €".
   *
   * @param wert der Betrag
   * @return der Betrag, wie er auf dem Beleg steht
   */
  static String betrag(final BigDecimal wert) {
    return String.format(Locale.GERMANY, "%,.2f €", wert);
  }

  /**
   * Eine Zahl ohne Nachnullen: „3", „2,5", „19", „19,5".
   *
   * @param wert die Zahl
   * @return die Zahl mit Komma, ohne Nullen hinter der letzten Stelle, die zaehlt
   */
  static String zahl(final BigDecimal wert) {
    return wert.stripTrailingZeros().toPlainString().replace('.', ',');
  }

  /**
   * Die gefuellten Werte in ihrer Reihenfolge; ein fehlender laesst keine Luecke.
   *
   * @param werte die Werte, jeder darf fehlen
   * @return die gefuellten
   */
  static List<String> gefuellt(final @Nullable String... werte) {
    return Arrays.stream(werte).filter(Objects::nonNull).toList();
  }

  /**
   * Die gefuellten Werte, mit dem Trenner verbunden.
   *
   * @param trenner was zwischen zwei Werten steht
   * @param werte die Werte, jeder darf fehlen
   * @return die Zeile, oder {@code null}, wenn keiner gefuellt ist
   */
  static @Nullable String verbunden(final String trenner, final @Nullable String... werte) {
    final List<String> teile = gefuellt(werte);
    if (teile.isEmpty()) {
      return null;
    }
    return String.join(trenner, teile);
  }

  /**
   * Der Wert mit seiner Beschriftung davor.
   *
   * @param beschriftung was vor dem Wert steht
   * @param wert der Wert, darf fehlen
   * @return die Zeile, oder {@code null}, wenn der Wert fehlt
   */
  static @Nullable String mit(final String beschriftung, final @Nullable String wert) {
    if (wert == null) {
      return null;
    }
    return beschriftung + wert;
  }

  /**
   * Postleitzahl und Ort, soweit vorhanden.
   *
   * @param anschrift die Anschrift
   * @return die Zeile, oder {@code null}, wenn beides fehlt
   */
  static @Nullable String ortszeile(final Anschrift anschrift) {
    return verbunden(" ", anschrift.plz(), anschrift.ort());
  }

  /**
   * Die Umsatzsteuer-Identifikationsnummer; ohne sie die Steuernummer.
   *
   * @param absender die eigenen Angaben
   * @return die Zeile, oder {@code null}, wenn beide fehlen
   */
  static @Nullable String steuerzeile(final Belegabsender absender) {
    if (absender.umsatzsteuerId() != null) {
      return mit("USt-IdNr. ", absender.umsatzsteuerId());
    }
    return mit("Steuernummer ", absender.steuernummer());
  }
}
