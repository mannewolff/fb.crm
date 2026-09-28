package org.mwolff.fbcrm.auftrag.application;

import org.jspecify.annotations.Nullable;

/**
 * Was die Angebotsansicht ueber den Auftrag wissen muss (Plan E3).
 *
 * <p>Zwei Auskuenfte auf einem Weg, weil die Ansicht beide zugleich braucht: die Zeile des Auftrags
 * — oder {@code null} — und ob sich heute einer anlegen laesst. Ein zweiter Aufruf fuer die zweite
 * Auskunft waere ein zweiter Zeitpunkt.
 *
 * <p><b>{@code anlegbar} sagt nichts darueber, ob schon ein Auftrag haengt.</b> Das sieht die
 * Oberflaeche daran, dass {@link #auftrag()} gefuellt ist; hier stehen Angebotszustand und
 * Abschlussstand des Vorgangs. Zweimal dasselbe auszudruecken hiesse, beide Werte in Einklang
 * halten zu muessen.
 *
 * @param auftrag der Auftrag zu diesem Angebot, oder {@code null} — hoechstens einer (F9)
 * @param anlegbar {@code true}, wenn Angebotszustand und Abschlussstand des Vorgangs das Anlegen
 *     heute zulassen (Kriterien 1, 11)
 */
public record AuftragAmAngebot(@Nullable AuftragAnsicht auftrag, boolean anlegbar) {}
