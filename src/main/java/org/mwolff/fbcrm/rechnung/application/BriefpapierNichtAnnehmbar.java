package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das hochgeladene Briefpapier wird nicht angenommen (Issue #361, Kriterium 2; Plan #363, E5).
 *
 * <p><b>Eine Ausnahme fuer sechs Gruende</b>, nach dem Muster von {@link DokumentNichtAnnehmbar}:
 * Alle sagen dasselbe an derselben Stelle mit demselben Statuscode — die Datei am Feld {@code
 * datei} geht nicht. Welcher Grund vorliegt, sagt allein der Satz; darum entsteht die Meldung zur
 * Laufzeit und steht nicht in {@code reason}.
 *
 * <p><b>Zwei Stellen weisen ab.</b> Die drei inhaltsunabhaengigen Gruende — leer, zu gross, kein
 * PDF an der Signatur — prueft der Anwendungsfall vor dem Port; ihre Erzeuger bleiben darum im
 * Paket. Die drei Gruende am geladenen Dokument — nicht ladbar oder verschluesselt, mehr als zwei
 * Seiten, nicht A4 hochkant — prueft die Umsetzung von {@link Briefpapierpruefer} in {@code
 * rechnung.infrastructure}; ihre Erzeuger sind darum oeffentlich.
 *
 * <p>Verschluesselt und nicht ladbar teilen einen Satz (E4): Ein PDF, das sich nicht oeffnen
 * laesst, und eines, das sich nur mit Schluessel lesen liesse, sind fuer den Betreiber dieselbe
 * Lage, und ein Unterschied im Satz verriete nur, wie weit der Parser kam.
 *
 * <p>422 wie die uebrigen Feldfehler dieses Projekts: Der Rumpf ist wohlgeformt, abgewiesen wird er
 * fachlich.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY)
public final class BriefpapierNichtAnnehmbar extends Feldfehler {

  /** Das Feld, unter dem Maske und Schnittstelle das Briefpapier fuehren. */
  public static final String FELD = "datei";

  /** Die Datei traegt kein einziges Byte. */
  public static final String MELDUNG_LEER = "Die Datei ist leer.";

  /** Die Datei ueberschreitet die eigene Grenze des Briefpapiers. */
  public static final String MELDUNG_ZU_GROSS = Briefpapiergrenze.MELDUNG;

  /** Der Inhalt ist kein PDF — erkannt an den ersten Bytes, nicht am Namen. */
  public static final String MELDUNG_KEIN_PDF = "Das Briefpapier muss ein PDF sein.";

  /** Das PDF ist verschluesselt oder laesst sich nicht lesen. */
  public static final String MELDUNG_NICHT_LADBAR =
      "Das PDF ist verschlüsselt oder lässt sich nicht lesen.";

  /** Das PDF hat mehr als zwei Seiten. */
  public static final String MELDUNG_ZU_VIELE_SEITEN =
      "Das Briefpapier darf höchstens 2 Seiten haben.";

  /** Eine Seite ist nicht A4 hochkant. */
  public static final String MELDUNG_FORMAT = "Das Briefpapier muss A4 hochkant sein.";

  private BriefpapierNichtAnnehmbar(final String meldung) {
    super(meldung);
  }

  /** Die Datei ist leer. */
  static BriefpapierNichtAnnehmbar leer() {
    return new BriefpapierNichtAnnehmbar(MELDUNG_LEER);
  }

  /** Die Datei ist groesser als {@link Briefpapiergrenze#MAX_BYTE}. */
  static BriefpapierNichtAnnehmbar zuGross() {
    return new BriefpapierNichtAnnehmbar(MELDUNG_ZU_GROSS);
  }

  /** Der Inhalt ist kein PDF. */
  static BriefpapierNichtAnnehmbar keinPdf() {
    return new BriefpapierNichtAnnehmbar(MELDUNG_KEIN_PDF);
  }

  /** Das PDF ist verschluesselt oder hat keine Seite. */
  public static BriefpapierNichtAnnehmbar nichtLadbar() {
    return new BriefpapierNichtAnnehmbar(MELDUNG_NICHT_LADBAR);
  }

  /**
   * Das PDF laesst sich nicht laden; der Satz ist derselbe wie bei {@link #nichtLadbar()}.
   *
   * @param ursache der Fehler des Parsers; er bleibt als Ursache am Stapel, der Satz nennt ihn
   *     nicht
   */
  public static BriefpapierNichtAnnehmbar nichtLadbar(final Throwable ursache) {
    final BriefpapierNichtAnnehmbar abweisung = nichtLadbar();
    abweisung.initCause(ursache);
    return abweisung;
  }

  /** Das PDF hat mehr als zwei Seiten. */
  public static BriefpapierNichtAnnehmbar zuVieleSeiten() {
    return new BriefpapierNichtAnnehmbar(MELDUNG_ZU_VIELE_SEITEN);
  }

  /** Eine Seite ist nicht A4 hochkant. */
  public static BriefpapierNichtAnnehmbar falschesFormat() {
    return new BriefpapierNichtAnnehmbar(MELDUNG_FORMAT);
  }

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(getMessage()));
  }
}
