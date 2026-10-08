package org.mwolff.fbcrm.angebot.web;

import org.jspecify.annotations.Nullable;

/**
 * Der Rumpf beim Anlegen eines Angebots (Issue #126).
 *
 * <p>Nur optionale Felder — und der ganze Rumpf darf fehlen: Die Firma steht im Pfad, der
 * Ansprechpartner ist optional, und ein Pflichtrumpf {@code {}} waere eine Formalie ohne Aussage.
 *
 * <p>Kein Feld fuer Text oder Positionen: Das frische Angebot wird vorbelegt und danach ueber den
 * Aenderungsweg gepflegt. Zwei Wege, auf denen ein Angebot Inhalt bekommt, liefen auseinander.
 *
 * <p>{@code intern} darf fehlen und ist dann {@code false}: Das Angebot an einen Kunden ist der
 * gewoehnliche Fall, und ein {@code null} soll denselben Weg gehen wie ein fehlendes Feld (Issue
 * #226).
 *
 * @param ansprechpartnerId Kennung des Ansprechpartners bei der Firma, oder {@code null}
 * @param intern ob das Angebot die eigene interne Arbeit festhaelt; {@code null} gilt als extern
 */
public record AngebotAnlegenRequest(@Nullable Long ansprechpartnerId, @Nullable Boolean intern) {}
