package org.mwolff.fbcrm.auftrag.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;

/**
 * Ein Auftrag samt der Nummer des Angebots, aus dem er entstand (Kriterium 3).
 *
 * <p>Die Nummer steht nicht am Auftrag: Er traegt die Kennung seiner Quelle, und eine Kennung zeigt
 * kein Wort. Sie wird darum beim Lesen dazugeholt — und <b>immer</b>, nicht nur auf dem Weg, auf
 * dem eine Ansicht sie gerade zeigt: Ein Feld, das je Weg mal da und mal nicht da ist, zwingt die
 * Oberflaeche zu zwei Formen fuer dieselbe Antwort.
 *
 * <p>Die Summe und die Positionsbetraege fehlen hier — sie sind reine Funktionen des Auftrags und
 * brauchen nichts von aussen (E11).
 *
 * @param auftrag der Auftrag samt seinen Positionen
 * @param angebotNummer Nummer des Quell-Angebots; {@code null} nur, solange es keine traegt
 */
public record AuftragAnsicht(Auftrag auftrag, @Nullable String angebotNummer) {}
