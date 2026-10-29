package org.mwolff.fbcrm.rechnung.domain;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;

/**
 * Der Empfaenger eines Belegs, wie er beim Stellen der Rechnung galt (R8).
 *
 * <p>Eine Kopie und kein Verweis: Zieht die Firma spaeter um, zeigt die gestellte Rechnung
 * weiterhin die Anschrift, die der Kunde auf seinem Dokument gelesen hat. Genau deshalb steht der
 * Name der Firma hier als Text und nicht als Kennung.
 *
 * <p>Ein Ansprechpartner ist nicht noetig — Kriterium 12 verlangt die Firmenanschrift, nicht eine
 * Person.
 *
 * <p>Das Land steht zweifach (Plan #461, E10): In der Anschrift der gedruckte Name, an neuen
 * Rechnungen der deutsche Name aus der Laenderliste, an alten der damalige Freitext; als {@code
 * landKuerzel} das Kuerzel fuer die E-Rechnung, an alten Rechnungen {@code null}.
 *
 * @param firma Name der Firma; Pflicht, sobald ein Beleg festgeschrieben ist
 * @param anschrift Postanschrift der Firma; jede ihrer Angaben darf fehlen
 * @param ansprechpartner Anrede und Name der Person, oder {@code null}
 * @param landKuerzel Kuerzel des Landes nach ISO 3166-1 alpha-2, oder {@code null}
 */
public record Belegempfaenger(
    String firma,
    Anschrift anschrift,
    @Nullable String ansprechpartner,
    @Nullable String landKuerzel) {}
