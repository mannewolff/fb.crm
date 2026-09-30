package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Der eingereichte Text eines Kommentars — beim Schreiben und beim Aendern derselbe Rumpf (Issue
 * #140, Kriterien 2 und 9).
 *
 * <p>{@code @NotBlank} weist einen leeren Text und einen Text aus Leerraum ab (Kriterium 7).
 * {@code @Size} misst den <b>eingereichten</b> Text und nicht den bereinigten (Plan #141, E7): Die
 * Oberflaeche schneidet vor dem Senden ab, fuer den Nutzer zaehlt damit der bereinigte Text. Die
 * Grenze steht ein zweites Mal als CHECK an der Tabelle, gemessen am gespeicherten Text — das eine
 * ersetzt das andere nicht.
 *
 * <p>Kein Feld fuer das Angebot und keines fuer einen Verfasser: Das Angebot steht im Pfad, und
 * einen Verfasser kennt die Anwendung nicht (E11).
 *
 * @param text der Text des Kommentars
 */
public record AngebotKommentarRequest(@NotBlank @Size(max = 2000) String text) {}
