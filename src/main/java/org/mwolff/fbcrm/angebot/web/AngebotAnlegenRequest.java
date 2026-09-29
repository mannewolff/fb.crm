package org.mwolff.fbcrm.angebot.web;

import org.jspecify.annotations.Nullable;

/**
 * Der Rumpf beim Anlegen eines Angebots (Issue #126).
 *
 * <p>Ein einziges, optionales Feld — und der ganze Rumpf darf fehlen: Die Firma steht im Pfad, der
 * Ansprechpartner ist optional, und ein Pflichtrumpf {@code {}} waere eine Formalie ohne Aussage.
 *
 * <p>Kein Feld fuer Text oder Positionen: Das frische Angebot wird vorbelegt und danach ueber den
 * Aenderungsweg gepflegt. Zwei Wege, auf denen ein Angebot Inhalt bekommt, liefen auseinander.
 *
 * @param ansprechpartnerId Kennung des Ansprechpartners bei der Firma, oder {@code null}
 */
public record AngebotAnlegenRequest(@Nullable Long ansprechpartnerId) {}
