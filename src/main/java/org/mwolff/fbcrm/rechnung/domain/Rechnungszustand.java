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
}
