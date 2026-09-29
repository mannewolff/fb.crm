package org.mwolff.fbcrm.angebot.application;

import org.mwolff.fbcrm.firma.domain.Ansprechpartner;

/**
 * Der Name eines Ansprechpartners, wie er auf Beleg und Ansicht steht: „Vorname Nachname", ohne
 * Vorname nur der Nachname.
 *
 * <p>Eine Regel an einer Stelle: Beleg und Ansicht nennen dieselbe Person, und zwei Abschriften
 * derselben Regel liefen beim ersten Nachziehen auseinander.
 */
final class Personenname {

  private Personenname() {}

  /** Der Name der Person; ein leerer Vorname zaehlt wie ein fehlender. */
  static String von(final Ansprechpartner person) {
    final String vorname = person.vorname();
    if (vorname == null || vorname.isBlank()) {
      return person.nachname();
    }
    return vorname + " " + person.nachname();
  }
}
