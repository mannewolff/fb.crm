package org.mwolff.fbcrm.angebot.web;

import org.jspecify.annotations.Nullable;

/**
 * Der Rumpf beim Anlegen eines Angebots (Kriterium 8, E23).
 *
 * <p>Ein einziges, optionales Feld — und der ganze Rumpf darf fehlen: „Angebot anlegen" ohne
 * Vorlage ist der Normalfall, und ein Pflichtrumpf {@code {}} waere eine Formalie ohne Aussage.
 *
 * <p>Kein Feld fuer Texte oder Positionen: Der frische Entwurf wird vorbelegt (Kriterium 3) und
 * danach ueber den Aenderungsweg des Angebots gepflegt. Zwei Wege, auf denen ein Entwurf Inhalt
 * bekommt, liefen auseinander.
 *
 * @param vorlageAngebotId Kennung des Angebots, dessen Texte und Positionen uebernommen werden,
 *     oder {@code null}
 */
public record AngebotAnlegenRequest(@Nullable Long vorlageAngebotId) {}
