package org.mwolff.fbcrm.firma.domain;

import org.jspecify.annotations.Nullable;

/**
 * Die Postanschrift einer Firma.
 *
 * <p>Jede Angabe darf fehlen: Eine Firma wird oft mit nichts als ihrem Namen angelegt und spaeter
 * vervollstaendigt. Fehlt eine Angabe, steht dort {@code null} und nicht der Leerstring — sonst
 * gaebe es zwei Schreibweisen fuer „nicht angegeben" (E9).
 *
 * @param strasse Strasse und Hausnummer
 * @param plz Postleitzahl
 * @param ort Ort
 * @param land Land
 */
public record Anschrift(
    @Nullable String strasse, @Nullable String plz, @Nullable String ort, @Nullable String land) {}
