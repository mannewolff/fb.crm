package org.mwolff.fbcrm.vorgang.application;

import java.util.List;

/**
 * Die Vorgaenge einer Firma, getrennt nach offen und abgeschlossen (Kriterium 12).
 *
 * <p>Getrennt und nicht als eine Liste mit Kennzeichen: Die Detailansicht der Firma zeigt die
 * abgeschlossenen abgesetzt unter den offenen, und die Trennung an einer Stelle zu machen ist
 * einfacher, als sie in jeder Ansicht nachzubauen.
 *
 * <p>Zwei leere Listen sind die Antwort fuer eine Firma ohne Vorgang — die Oberflaeche sagt das
 * dann selbst; eine eigene Meldung braucht sie dafuer nicht.
 *
 * @param offene die offenen Vorgaenge in der Reihenfolge der Uebersicht (E16)
 * @param abgeschlossene die abgeschlossenen, in derselben Reihenfolge
 */
public record VorgaengeDerFirma(List<VorgangZeile> offene, List<VorgangZeile> abgeschlossene) {}
