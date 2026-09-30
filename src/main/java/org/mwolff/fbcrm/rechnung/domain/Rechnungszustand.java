package org.mwolff.fbcrm.rechnung.domain;

/**
 * Wie weit eine Rechnung gediehen ist (Plan #169, E3).
 *
 * <p>Zwei Zustaende und kein dritter: Solange sie Entwurf ist, laesst sich alles an ihr aendern und
 * sie laesst sich loeschen; ab dem Stellen traegt sie ihre Nummer, ihren Steuersatz, ihr
 * Zahlungsziel und die Kopien von Absender und Empfaenger, und nichts davon aendert sich noch.
 *
 * <p>Die Werte gehen als Text in die Datenbank; der CHECK {@code rechnung_zustand} in {@code
 * V18__rechnung.sql} nennt dieselben zwei.
 */
public enum Rechnungszustand {

  /** Erfasst, noch nicht gestellt: aenderbar und loeschbar, ohne Nummer und ohne Dokument. */
  ENTWURF,

  /** Gestellt und damit festgeschrieben. */
  GESTELLT
}
