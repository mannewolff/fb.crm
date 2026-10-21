package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 *
 * <p>Auch die festen Saetze des Belegs stehen hier — Anrede, Einleitung, Zahlungsziel und Gruss —,
 * dazu Ort und Datum: Auf dem Briefbogen bleiben sie, wie sie auf der Kupferwolke sind (fachliche
 * Quelle #361, Geklaerter Stand 10), und ein zweiter Wortlaut wuerde an einer Stelle altern.
 */
final class Belegschreibweisen {

  /** Die Anrede; sie nennt keine Person, weil der Beleg an die Firma geht. */
  static final String ANREDE = "Sehr geehrte Damen und Herren,";

  /** Der Gruss; eine Unterschrift steht nicht darunter. */
  static final String GRUSS = "Mit freundlichen Grüßen";

  /** Das Datum des Belegs: „28.09.2026". */
  private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

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

  /**
   * Der Satz, der die Tabelle ankuendigt; mit Leistungszeitraum nennt er ihn.
   *
   * @param leistungszeitraum der Zeitraum der Leistung als Text, oder {@code null}
   * @return der Einleitungssatz
   */
  static String einleitungssatz(final @Nullable String leistungszeitraum) {
    if (leistungszeitraum == null) {
      return "Für meine Leistungen stelle ich Ihnen in Rechnung";
    }
    return "Für meine Leistungen im " + leistungszeitraum + " stelle ich Ihnen in Rechnung";
  }

  /**
   * Der Satz ueber das Zahlungsziel.
   *
   * @param zahlungszielTage das Zahlungsziel in Tagen; 0 bedeutet sofort faellig
   * @return der Zahlungssatz
   */
  static String zahlungssatz(final int zahlungszielTage) {
    if (zahlungszielTage == 0) {
      return "Bitte überweisen Sie den Betrag sofort";
    }
    return "Bitte überweisen Sie den Betrag innerhalb von " + zahlungszielTage + " Tagen";
  }

  /**
   * Ort und Datum: „Bremen, 28.09.2026"; ohne Ort nur das Datum.
   *
   * @param ort der eigene Ort, oder {@code null}
   * @param datum das Datum des Belegs
   * @return die Zeile
   */
  static String ortUndDatum(final @Nullable String ort, final LocalDate datum) {
    final String tag = DATUM.format(datum);
    if (ort == null) {
      return tag;
    }
    return ort + ", " + tag;
  }
}
