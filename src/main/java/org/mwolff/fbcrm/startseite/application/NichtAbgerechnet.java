package org.mwolff.fbcrm.startseite.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Die Kennzahl „Noch nicht abgerechnet" (#206, Kriterien 5 und 6).
 *
 * <p><b>Zwei Betraege mit verschiedenen Zeitraeumen</b>, und das ist Absicht. {@link #betrag()} ist
 * der Stand von heute ueber alle Monate: was aus erfasster Arbeitszeit noch in Rechnung gestellt
 * werden kann — je Position die erfassten Stunden, hoechstens die angebotene Menge, abzueglich der
 * Stunden auf gestellten Rechnungen, nie unter 0. {@link #erfasstImMonat()} haengt dagegen am
 * gewaehlten Monat und zeigt den Wert der dort erfassten Stunden <b>ohne</b> Deckel und <b>ohne</b>
 * Abzug (Kriterium 6, Plan #208, E13).
 *
 * <p>Der Grund fuer den Unterschied: Eine Rechnung haelt nicht fest, aus welchem Monat ihre Stunden
 * stammen (#206, Antwort 2). „Davon im Monat noch nicht abgerechnet" waere darum nicht bestimmbar;
 * die Monatszeile sagt stattdessen, was in diesem Monat an Arbeit angefallen ist.
 *
 * @param betrag der Betrag netto, Stand von heute ueber alle Monate
 * @param erfasstImMonat der Wert der im gewaehlten Monat erfassten Stunden, netto
 * @param anteile je beitragendem Angebot sein Anteil am {@code betrag}, neueste zuerst
 */
public record NichtAbgerechnet(
    BigDecimal betrag, BigDecimal erfasstImMonat, List<Angebotsanteil> anteile) {

  /** Nimmt die Anteile als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public NichtAbgerechnet {
    anteile = List.copyOf(anteile);
  }
}
