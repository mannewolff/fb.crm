package org.mwolff.fbcrm.eigeneangaben.application;

import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;

/**
 * Die eigenen Angaben samt dem Stand, den die Maske beim naechsten Speichern vorlegt (Plan #415,
 * E1, E2).
 *
 * @param angaben die gespeicherten Angaben
 * @param version der Stand der Zeile, wie ihn {@code If-Match} vorlegt
 */
public record GespeicherteEigeneAngaben(EigeneAngaben angaben, long version) {}
