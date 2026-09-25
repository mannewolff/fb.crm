/**
 * Die Schnittstelle des Vorgangs: Controller und die Records, die nach aussen gehen.
 *
 * <p>Hier liegt auch der Weg {@code /api/firmen/{firmaId}/vorgaenge} (E2) — er steht im Pfad der
 * Firma, gehoert aber diesem Modul: Nur so kommt die Vorgangsliste in die Detailansicht der Firma,
 * ohne dass {@code firma} etwas vom Vorgang wuesste.
 */
@NullMarked
package org.mwolff.fbcrm.vorgang.web;

import org.jspecify.annotations.NullMarked;
