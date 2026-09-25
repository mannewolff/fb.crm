package org.mwolff.fbcrm.vorgang.application;

import java.time.Instant;
import org.mwolff.fbcrm.vorgang.domain.Phase;

/**
 * Eine Zeile der Vorgangsliste — in der Uebersicht wie in der Liste an der Firma.
 *
 * <p>Sie traegt genau das, was die Listen zeigen (Kriterien 2, 12), nicht den ganzen Vorgang:
 * Ansprechpartner, Zeitstempel und Historie gehoeren in die Detailansicht und haetten in einer
 * Liste von hundert Zeilen nur Gewicht ohne Nutzen.
 *
 * @param id technische Id des Vorgangs
 * @param nummer fortlaufende Vorgangsnummer; als Zahl, das {@code #} setzt die Oberflaeche
 * @param titel Titel des Vorgangs
 * @param firmaName Name der zugeordneten Firma
 * @param phase abgeleitete Phase (E4)
 * @param abgeschlossen {@code true}, solange der Vorgang abgeschlossen ist — daran haengt die
 *     Kennzeichnung in der Liste (Kriterium 12)
 * @param letzteAktivitaet Zeitpunkt des juengsten Eintrags; ohne Eintrag der Anlagezeitpunkt des
 *     Vorgangs (Kriterium 2)
 */
public record VorgangZeile(
    long id,
    long nummer,
    String titel,
    String firmaName,
    Phase phase,
    boolean abgeschlossen,
    Instant letzteAktivitaet) {}
