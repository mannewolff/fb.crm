package org.mwolff.fbcrm.rechnung.domain;

/**
 * Wie weit eine Rechnung gediehen ist und wie die Forderung ausging (Plan #169, E3; Issue #253).
 *
 * <p>Vier Zustaende: Solange sie Entwurf ist, laesst sich alles an ihr aendern und sie laesst sich
 * loeschen; ab dem Stellen traegt sie ihre Nummer, ihren Steuersatz, ihr Zahlungsziel und die
 * Kopien von Absender und Empfaenger, und nichts davon aendert sich noch. Die beiden letzten sagen,
 * wie es ausgegangen ist — der Kunde hat gezahlt, oder er wird nicht mehr zahlen.
 *
 * <p><b>Bezahlt und Abgeschrieben sind weiterhin gestellte Rechnungen</b> ({@link #istGestellt()}).
 * Nummer, Festschreibung, Dokument und die abgerechneten Mengen bleiben unveraendert; der Zustand
 * sagt nur, wie die Forderung ausgegangen ist. Jede Pruefung, die bisher auf {@code == GESTELLT}
 * stand, fragt darum diese Methode: Sonst gaebe eine bezahlte Rechnung ihre Positionsmengen frei,
 * verschwaende aus der Monatsabrechnung und verlore ihr Dokument.
 *
 * <p><b>Offen ist dagegen allein Gestellt</b> ({@link #istOffen()}, Issue #284): Wer wissen will,
 * wo noch Geld fehlt, fragt nicht nach der Festschreibung, sondern nach dem Ausgang. Die beiden
 * Fragen stehen nebeneinander, weil sie Verschiedenes beantworten — „ist draussen" und „ist
 * bezahlt".
 *
 * <p>Ein Zahlungsdatum oder ein gezahlter Betrag steht nicht dabei: Der Zahlungseingang liegt laut
 * CLAUDE.md ausserhalb des Umfangs.
 *
 * <p>Die Werte gehen als Text in die Datenbank; der CHECK {@code rechnung_zustand} in {@code
 * V21__rechnung_zustand_erledigt.sql} nennt dieselben vier.
 */
public enum Rechnungszustand {

  /** Erfasst, noch nicht gestellt: aenderbar und loeschbar, ohne Nummer und ohne Dokument. */
  ENTWURF,

  /** Gestellt und damit festgeschrieben; der Ausgang steht noch offen. */
  GESTELLT,

  /** Gestellt und bezahlt: die Forderung ist erledigt. */
  BEZAHLT,

  /** Gestellt und abgeschrieben: die Forderung kommt nicht mehr herein, etwa bei Insolvenz. */
  ABGESCHRIEBEN;

  /**
   * Ob die Rechnung gestellt ist — jeder Zustand ausser {@link #ENTWURF}.
   *
   * <p>Die eine Frage statt eines Vergleichs mit {@link #GESTELLT}: Was eine gestellte Rechnung
   * ausmacht — Nummer, Dokument, die abgerechnete Menge, der Eingang in die Monatsabrechnung — gilt
   * fuer alle drei gleichermassen. Aufgezaehlt wird dabei der Entwurf und nicht die Gegenseite: So
   * faellt ein spaeterer fuenfter Zustand von selbst unter „gestellt" und nicht durch das Raster.
   */
  public boolean istGestellt() {
    return this != ENTWURF;
  }

  /**
   * Ob die Forderung noch offen ist — allein {@link #GESTELLT} (Issue #284).
   *
   * <p>Die zweite Frage neben {@link #istGestellt()}, und beide sagen Verschiedenes: Jene sagt,
   * dass die Rechnung draussen ist und darum in den Umsatz des Monats zaehlt, diese, dass noch Geld
   * fehlt. Die Startseite braucht beide — „Abgerechnet" fragt die erste, „Offene Rechnungen" und
   * die Spalte „Offen" ihrer Monatsliste die zweite.
   *
   * <p><b>Abgeschrieben ist nicht offen.</b> Die Forderung kommt nicht mehr herein; sie als offen
   * zu zeigen hiesse, auf Geld zu warten, das niemand mehr schickt. Der Entwurf ist es ebenso nicht
   * — er ist noch gar nicht draussen.
   *
   * <p>Hier wird der eine Zustand genannt und nicht die Gegenseite aufgezaehlt, anders als in
   * {@link #istGestellt()}: „Offen" ist der Zustand vor jedem Ausgang, und ein spaeterer fuenfter
   * waere wieder ein Ausgang. Er faellt damit von selbst unter „nicht offen" — und das ist die
   * vorsichtige Seite: Eine Kennzahl, die zu wenig Offenes zeigt, erfindet keinen Posten.
   */
  public boolean istOffen() {
    return this == GESTELLT;
  }

  /**
   * Ob der Wechsel des Ausgangs von {@code von} nach {@code nach} erlaubt ist (Issue #253).
   *
   * <p>Die eine Stelle fuer beide Aggregate — die Rechnung und die nachgetragene Rechnung fragen
   * hier (Plan #259, E4). Zwei Abschriften derselben Regel liefen beim ersten Nachziehen
   * auseinander.
   *
   * <p>Drei Kanten und keine vierte: von {@code GESTELLT} zu einem der beiden Ausgaenge und von
   * jedem Ausgang zurueck auf {@code GESTELLT}. Beide Seiten muessen gestellt sein — der Entwurf
   * liegt davor, und ihn stellt {@code Rechnung.gestellt} mit Nummer und Kopien —, und genau eine
   * der beiden muss {@code GESTELLT} sein. Diese zweite Bedingung schliesst zugleich den Stillstand
   * aus und den direkten Weg zwischen den Ausgaengen: Ein Wechsel von bezahlt auf abgeschrieben
   * fuehrt ueber „gestellt".
   *
   * @param von der heutige Zustand
   * @param nach der verlangte Zustand
   */
  public static boolean ausgangswechselErlaubt(
      final Rechnungszustand von, final Rechnungszustand nach) {
    return von.istGestellt() && nach.istGestellt() && (von == GESTELLT) != (nach == GESTELLT);
  }
}
