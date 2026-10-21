package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;

/**
 * Schalter und Raender des Briefbogens, wie die Maske sie schickt (fachliche Quelle #361, Kriterien
 * 2, 3, 13 und 15; Plan #363, E8).
 *
 * <p>Jeder Wert ist Pflicht: Die Maske zeigt den ganzen Satz und schickt ihn ganz zurueck, und
 * jeder Wert hat eine Vorgabe — „nicht angegeben" gibt es hier nicht. Die Raender kommen in ganzen
 * <b>Zehntel-Millimetern</b>.
 *
 * <p>{@code @Min(0)} ist der erste Riegel gegen einen negativen Rand (Kriterium 13): Ohne ihn liefe
 * der Wert in den Kompaktkonstruktor von {@link Randmasse}, und die Antwort waere ein 500 statt
 * einer Meldung am Feld. Der Konstruktor bleibt der zweite.
 *
 * @param absenderzeile ob die Absenderzeile eingedruckt wird
 * @param steuerBankZeile ob die Steuer- und Bankzeile eingedruckt wird
 * @param links linker Rand
 * @param rechts rechter Rand
 * @param inhaltOben Beginn des Inhalts auf Seite 1
 * @param unten unterer Rand auf Seite 1
 * @param folgeOben oberer Rand auf Folgeseiten
 * @param folgeUnten unterer Rand auf Folgeseiten
 */
public record BriefbogenRequest(
    @NotNull Boolean absenderzeile,
    @NotNull Boolean steuerBankZeile,
    @NotNull @Min(0) Integer links,
    @NotNull @Min(0) Integer rechts,
    @NotNull @Min(0) Integer inhaltOben,
    @NotNull @Min(0) Integer unten,
    @NotNull @Min(0) Integer folgeOben,
    @NotNull @Min(0) Integer folgeUnten) {

  /** Die sechs Raender in der Sprache der Fachschicht. */
  Randmasse raender() {
    return new Randmasse(links, rechts, inhaltOben, unten, folgeOben, folgeUnten);
  }
}
