package org.mwolff.fbcrm.arbeitszeit.application;

import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;

/**
 * Ein Zeiteintrag mit der Position, auf die er gebucht ist (Plan #194, A20), und der Person, der er
 * gehoert (Plan #426, E14).
 *
 * <p>Der Eintrag selbst traegt nur die Kennung seiner Position — das ist alles, was der Bestand
 * fuehrt. Die Zeile der Monatsliste zeigt aber Bezeichnung, Angebot und Firma (Issue #193,
 * Kriterium 5), und genau diese Anreicherung steht hier daneben. Derselbe Zuschnitt wie {@code
 * angebot.application.AngebotMitFirma}: der Datensatz und das eine, was die Ansicht zusaetzlich
 * braucht.
 *
 * <p><b>Die Person steht an jeder Zeile</b>, auch in der Sicht einer einzelnen Person (E14): Eine
 * Antwort, deren Felder von der Auswahl abhaengen, zwaenge den Leser zu einer Fallunterscheidung
 * ueber etwas, das er nicht entschieden hat. Ihr Name ist der aktuelle des Kontos und keine Kopie
 * (fachliche Quelle #410, Kriterium 9) — derselbe Zuschnitt wie im Umschalter, darum dieselbe
 * {@link WaehlbarePerson}.
 *
 * @param eintrag der Zeiteintrag, wie der Bestand ihn fuehrt
 * @param position die Position, auf die gebucht wurde
 * @param person die Person, der der Eintrag gehoert, mit ihrem aktuellen Namen
 */
public record Zeitbuchung(Zeiteintrag eintrag, Buchungsposition position, WaehlbarePerson person) {}
