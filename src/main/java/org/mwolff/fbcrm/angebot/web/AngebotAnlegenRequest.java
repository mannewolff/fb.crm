package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Der Rumpf beim Anlegen eines Angebots (Issue #126).
 *
 * <p><b>Der Rumpf ist Pflicht</b> (Issue #317, Plan #315 E4): Er traegt den Titel, und ohne Titel
 * entsteht kein Angebot (#314, Kriterium 2). {@code @NotBlank} nennt das Feld beim Namen; ein
 * fehlender Rumpf koennte das nicht. Die Firma steht im Pfad, der Ansprechpartner ist optional.
 *
 * <p>Kein Feld fuer Text oder Positionen: Das frische Angebot wird vorbelegt und danach ueber den
 * Aenderungsweg gepflegt. Zwei Wege, auf denen ein Angebot Inhalt bekommt, liefen auseinander. Der
 * Titel ist davon ausgenommen: Er ist die Benennung, ohne die das Angebot gar nicht entsteht, und
 * kein Inhalt, der spaeter dazukommt.
 *
 * <p>{@code intern} darf fehlen und ist dann {@code false}: Das Angebot an einen Kunden ist der
 * gewoehnliche Fall, und ein {@code null} soll denselben Weg gehen wie ein fehlendes Feld (Issue
 * #226).
 *
 * @param titel die einzeilige Benennung des Angebots, hoechstens 200 Zeichen
 * @param ansprechpartnerId Kennung des Ansprechpartners bei der Firma, oder {@code null}
 * @param intern ob das Angebot die eigene interne Arbeit festhaelt; {@code null} gilt als extern
 */
public record AngebotAnlegenRequest(
    @NotBlank @Size(max = 200) String titel,
    @Nullable Long ansprechpartnerId,
    @Nullable Boolean intern) {}
