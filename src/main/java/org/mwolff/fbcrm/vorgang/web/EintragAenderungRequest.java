package org.mwolff.fbcrm.vorgang.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Die Eingaben beim Aendern eines Eintrags (Kriterien 18, 19) — als JSON, ohne Datei.
 *
 * <p><b>Kein Feld fuer die Art und keines fuer die Datei.</b> Beide stehen im Bestand und sind
 * nicht aenderbar: Ein Anhang laesst sich nicht austauschen, wer eine andere Datei meint, haengt
 * sie an. Ein Feld dafuer waere ein zweiter, widersprechbarer Ort fuer dieselbe Angabe.
 *
 * <p>Der Text ist Pflicht — auch beim Anhang, dessen Beschreibung beim Anlegen freiwillig war. Das
 * Aendern setzt den Text auf einen Wert; „setze ihn auf nichts" verlangt kein Kriterium, und eine
 * Regel, die die Art kennen muesste, koennte diese Anfrage nicht tragen. So steht die Meldung als
 * {@code fieldErrors.text} am Feld, und die Schranke in der Domaene bleibt der Notnagel.
 *
 * @param text der neue Text
 * @param geschehenAm der neue Zeitpunkt des Geschehens
 */
@EintragConstraint
public record EintragAenderungRequest(@NotBlank String text, @NotNull Instant geschehenAm) {}
