package org.mwolff.fbcrm.arbeitszeit.application;

import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;

/**
 * Ein Zeiteintrag mit der Position, auf die er gebucht ist (Plan #194, A20).
 *
 * <p>Der Eintrag selbst traegt nur die Kennung seiner Position — das ist alles, was der Bestand
 * fuehrt. Die Zeile der Monatsliste zeigt aber Bezeichnung, Angebot und Firma (Issue #193,
 * Kriterium 5), und genau diese Anreicherung steht hier daneben. Derselbe Zuschnitt wie {@code
 * angebot.application.AngebotMitFirma}: der Datensatz und das eine, was die Ansicht zusaetzlich
 * braucht.
 *
 * @param eintrag der Zeiteintrag, wie der Bestand ihn fuehrt
 * @param position die Position, auf die gebucht wurde
 */
public record Zeitbuchung(Zeiteintrag eintrag, Buchungsposition position) {}
