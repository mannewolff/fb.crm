package org.mwolff.fbcrm.angebot.domain;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;

/**
 * Der Empfaenger eines Belegs, wie er beim Versenden galt (R8).
 *
 * <p>Eine Kopie und kein Verweis: Zieht die Firma spaeter um, zeigt das versendete Angebot
 * weiterhin die Anschrift, die der Kunde auf seinem Dokument gelesen hat. Genau deshalb steht der
 * Name der Firma hier als Text und nicht als Kennung.
 *
 * <p>Ein Ansprechpartner ist nicht noetig — Kriterium 12 verlangt die Firmenanschrift, nicht eine
 * Person.
 *
 * @param firma Name der Firma; Pflicht, sobald ein Beleg festgeschrieben ist
 * @param anschrift Postanschrift der Firma; jede ihrer Angaben darf fehlen
 * @param ansprechpartner Anrede und Name der Person, oder {@code null}
 */
public record Belegempfaenger(
    String firma, Anschrift anschrift, @Nullable String ansprechpartner) {}
