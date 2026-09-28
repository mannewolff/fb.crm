package org.mwolff.fbcrm.auftrag.domain;

/**
 * Der Status eines Auftrags (Kriterium 7).
 *
 * <p>Drei Werte ohne Anzeigewort — und das mit Absicht (E18): {@code
 * angebot.domain.Angebotszustand} traegt auch keines, die Worte der Oberflaeche stehen in {@code
 * frontend/src/lib/}, und das Wort fuer den Ereignistext der Historie entsteht in {@code
 * auftrag.application}. Zwei Orte fuer dasselbe Wort laufen beim ersten Nachziehen auseinander.
 *
 * <p>Der Status wechselt in jede Richtung frei (F6); es gibt keinen endgueltigen Wert, den ein
 * Uebergang ausschliessen muesste.
 */
public enum Auftragsstatus {

  /** Beauftragt, aber noch nicht begonnen. */
  OFFEN,

  /** Die Arbeit laeuft. */
  IN_ARBEIT,

  /** Die Leistung ist erbracht; der Auftrag zaehlt nicht mehr zum Auftragsbestand. */
  ABGESCHLOSSEN
}
