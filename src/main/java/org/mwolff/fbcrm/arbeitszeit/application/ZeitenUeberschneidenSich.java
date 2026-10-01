package org.mwolff.fbcrm.arbeitszeit.application;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der eingereichte Zeitraum liegt ueber einem Eintrag, der schon da ist (Issue #193, Kriterium 4;
 * Plan #194, A8).
 *
 * <p><b>Gezaehlt wird ueber alle Positionen</b> und nicht nur ueber dieselbe: Niemand arbeitet zur
 * selben Zeit fuer zwei Kunden. Beruehrende Grenzen sind kein Widerspruch — 9:00 bis 10:00 und
 * 10:00 bis 11:00 sind zwei Eintraege.
 *
 * <p><b>Die Meldung nennt den anderen Eintrag beim Namen</b> — Uhrzeit, Position und Firma. Ein
 * blosses „Die Zeiten ueberschneiden sich" liesse den Anwender seinen Monat durchsuchen; der andere
 * Eintrag kann auf einem ganz anderen Angebot liegen. Darum entsteht der Text hier und nicht als
 * {@code @ResponseStatus(reason = …)}: Er nennt Werte und ist nicht fuer alle Faelle derselbe. Der
 * {@code GlobalExceptionHandler} nimmt {@code getMessage()} als {@code detail} der Antwort — so
 * traegt dieselbe Stelle beides.
 *
 * <p>Die Meldung haengt an <b>beiden</b> Zeitfeldern: Erst Beginn und Ende zusammen ergeben die
 * Lage, und der Anwender kann an jedem von beiden ausweichen.
 *
 * <p>422 und nicht 409: Es ist kein gleichzeitiger zweiter Schreiber im Spiel (CLAUDE.md,
 * „Betriebsform"), sondern eine Eingabe, die zum eigenen Bestand nicht passt. Ein {@link
 * Feldfehler} aus demselben Grund wie bei {@code angebot.application.PositionInRechnungVerwendet}.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY)
public final class ZeitenUeberschneidenSich extends Feldfehler {

  /** Das Feld, unter dem die Maske den Beginn fuehrt. */
  public static final String FELD_VON = "von";

  /** Das Feld, unter dem die Maske das Ende fuehrt. */
  public static final String FELD_BIS = "bis";

  /*
   * „9:00" und nicht „09:00": So steht die Uhrzeit auch in der Monatsliste. Die Zone spielt keine
   * Rolle, die Sprache schon — ofPattern ohne Locale nimmt die des Betriebssystems und koennte
   * andere Ziffern setzen.
   */
  private static final DateTimeFormatter UHRZEIT =
      DateTimeFormatter.ofPattern("H:mm", Locale.GERMAN);

  private final LocalTime andererVon;
  private final LocalTime andererBis;
  private final String bezeichnung;
  private final String firmaName;

  /**
   * @param andererVon Beginn des Eintrags, der schon da ist
   * @param andererBis Ende dieses Eintrags
   * @param bezeichnung Bezeichnung der Position, auf die dieser Eintrag gebucht ist
   * @param firmaName Name der Firma, zu deren Angebot diese Position gehoert
   */
  ZeitenUeberschneidenSich(
      final LocalTime andererVon,
      final LocalTime andererBis,
      final String bezeichnung,
      final String firmaName) {
    super();
    this.andererVon = andererVon;
    this.andererBis = andererBis;
    this.bezeichnung = bezeichnung;
    this.firmaName = firmaName;
  }

  @Override
  public String getMessage() {
    return "Überschneidet sich mit "
        + UHRZEIT.format(andererVon)
        + " bis "
        + UHRZEIT.format(andererBis)
        + ", "
        + bezeichnung
        + " ("
        + firmaName
        + ").";
  }

  @Override
  public Map<String, List<String>> felder() {
    final List<String> meldung = List.of(getMessage());
    return Map.of(FELD_VON, meldung, FELD_BIS, meldung);
  }
}
