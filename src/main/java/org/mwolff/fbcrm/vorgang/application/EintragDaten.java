package org.mwolff.fbcrm.vorgang.application;

import java.io.InputStream;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;

/**
 * Die Eingaben fuer einen neuen Eintrag der Historie, in der Sprache der Anwendungsschicht.
 *
 * <p>Der Datenstrom steht hier und nicht die hochgeladene Datei selbst: {@code MultipartFile} ist
 * ein Typ der Web-Schicht, und die Anwendungsschicht soll von HTTP nichts wissen. Aus demselben
 * Grund reist der Dateiname bereits gesaeubert an (E13) — die Saeuberung gehoert an den Rand, an
 * dem die Eingabe ankommt.
 *
 * <p>Wer den Strom schliesst, steht im Port {@code AnhangSpeicher}: der Aufrufer, also {@link
 * EintragHinzufuegenUseCase}.
 *
 * @param art Kommentar oder Anhang
 * @param text der Text; beim Kommentar Pflicht, beim Anhang die Beschreibung oder {@code null}
 * @param geschehenAm Zeitpunkt des Geschehens
 * @param dateiName gesaeuberter Dateiname — nur beim Anhang gesetzt
 * @param dateiGroesse Groesse in Byte — beim Kommentar {@code 0}
 * @param inhalt der Datenstrom der Datei — nur beim Anhang gesetzt
 */
public record EintragDaten(
    Eintragsart art,
    @Nullable String text,
    Instant geschehenAm,
    @Nullable String dateiName,
    long dateiGroesse,
    @Nullable InputStream inhalt) {

  /** Ein Kommentar: nur Text und Zeitpunkt, keine Datei. */
  public static EintragDaten kommentar(final @Nullable String text, final Instant geschehenAm) {
    return new EintragDaten(Eintragsart.KOMMENTAR, text, geschehenAm, null, 0L, null);
  }

  /** Ein Anhang: Datei samt Name und Groesse, der Text ist die freiwillige Beschreibung. */
  public static EintragDaten anhang(
      final @Nullable String text,
      final Instant geschehenAm,
      final String dateiName,
      final long dateiGroesse,
      final InputStream inhalt) {
    return new EintragDaten(Eintragsart.ANHANG, text, geschehenAm, dateiName, dateiGroesse, inhalt);
  }
}
