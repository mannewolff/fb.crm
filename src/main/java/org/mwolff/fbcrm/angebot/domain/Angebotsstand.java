package org.mwolff.fbcrm.angebot.domain;

/**
 * Der Stand eines Angebots, wie ihn die Oberflaeche zeigt (Kriterium 18).
 *
 * <p>Die sechs Werte sind die fuenf gespeicherten Zustaende plus {@link #ABGELAUFEN}. „Abgelaufen"
 * steht in keiner Spalte: Es ist ein versendetes Angebot mit verstrichener Gueltigkeit und entsteht
 * beim Lesen aus dem Vergleich mit dem heutigen Tag (E4, {@link Angebot#stand}). Eine Spalte dafuer
 * muesste taeglich von einem Zeitgeber umgeschrieben werden, um wahr zu bleiben.
 */
public enum Angebotsstand {

  /** Noch in Arbeit, ohne Nummer und ohne Dokument. */
  ENTWURF,

  /** Festgeschrieben und beim Kunden, Gueltigkeit noch nicht verstrichen. */
  VERSENDET,

  /** Festgeschrieben, Gueltigkeit verstrichen — und weiterhin offen fuer eine Reaktion. */
  ABGELAUFEN,

  /** Der Kunde hat zugesagt; endgueltig. */
  ANGENOMMEN,

  /** Der Kunde hat abgesagt; endgueltig. */
  ABGELEHNT,

  /** Durch ein spaeteres Angebot desselben Vorgangs ersetzt (Kriterium 19). */
  ABGELOEST
}
