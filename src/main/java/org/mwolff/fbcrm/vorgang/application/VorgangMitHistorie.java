package org.mwolff.fbcrm.vorgang.application;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.vorgang.domain.Phase;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;

/**
 * Die Detailansicht eines Vorgangs in einem Stueck: der Vorgang, seine Zuordnung und die
 * vollstaendige Historie (Kriterium 9, E25).
 *
 * <p>Ohne Seitenteilung und ohne zweiten Leseweg: Die Historie <b>ist</b> die Detailansicht; zwei
 * Wege bedeuteten zwei Ladezustaende fuer eine Ansicht.
 *
 * <p>Firma und Ansprechpartner stehen als ganze Domaenenobjekte da — nach dem Muster von {@code
 * FirmaMitAnsprechpartnern}. Sie tragen damit ihren Stilllegungsstand mit, und genau den braucht
 * Kriterium 23: Eine stillgelegte Zuordnung bleibt sichtbar und wird gekennzeichnet.
 *
 * <p>Die Phase steht als eigene Angabe daneben und nicht im Vorgang: Sie wird abgeleitet (E4) und
 * braucht dazu den Belegstand, den der Vorgang selbst nicht kennt. Hier steht sie fertig
 * ausgerechnet, damit die Schnittstellenschicht sie nicht ein zweites Mal herleiten muss.
 *
 * @param vorgang der Vorgang selbst
 * @param phase die abgeleitete Phase (Kriterien 11, 22)
 * @param firma die zugeordnete Firma, aktiv oder stillgelegt
 * @param ansprechpartner der zugeordnete Ansprechpartner, oder {@code null}
 * @param historie alle Eintraege, juengstes Geschehen oben (Kriterium 15)
 */
public record VorgangMitHistorie(
    Vorgang vorgang,
    Phase phase,
    Firma firma,
    @Nullable Ansprechpartner ansprechpartner,
    List<EintragAnsicht> historie) {}
