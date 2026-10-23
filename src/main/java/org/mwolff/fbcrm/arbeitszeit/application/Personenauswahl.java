package org.mwolff.fbcrm.arbeitszeit.application;

/**
 * Wessen Zeiten die Monatsliste zeigt: die einer Person oder die aller (Plan #426, E10; fachliche
 * Quelle #410, Kriterien 2 bis 4).
 *
 * <p><b>Die Textform ist die des Parameters {@code person}</b>: die Kennung eines Kontos oder das
 * Wort {@code alle}. Lesen ({@link #aus(String)}) und Schreiben ({@link #alsParameter()}) stehen
 * hier nebeneinander, damit die Antwort genau das nennt, was die Ansicht beim naechsten Aufruf
 * wieder schicken kann — zwei Abschriften derselben Form liefen beim ersten Nachziehen auseinander.
 *
 * <p>Ob eine genannte Person waehlbar ist und ob ein Konto ueberhaupt waehlen darf, entscheidet
 * {@link ArbeitszeitMonatUseCase}; dieser Typ sagt nur, was gewaehlt ist.
 */
public sealed interface Personenauswahl {

  /** Das Wort, mit dem der Parameter {@code person} alle Personen meint. */
  String ALLE = "alle";

  /** Die Auswahl in der Textform des Parameters {@code person}. */
  String alsParameter();

  /**
   * Die Auswahl zu einem Wert des Parameters {@code person}.
   *
   * @param parameter die Kennung eines Kontos oder das Wort {@code alle}
   * @throws PersonNichtWaehlbar wenn der Wert weder das eine noch das andere ist
   */
  static Personenauswahl aus(final String parameter) {
    if (ALLE.equals(parameter)) {
      return new Alle();
    }
    try {
      return new Einzeln(Long.parseLong(parameter));
    } catch (final NumberFormatException keineKennung) {
      throw new PersonNichtWaehlbar(keineKennung);
    }
  }

  /** Die Zeiten aller Personen. */
  record Alle() implements Personenauswahl {

    @Override
    public String alsParameter() {
      return ALLE;
    }
  }

  /**
   * Die Zeiten einer Person.
   *
   * @param accountId Kennung des Kontos, dessen Zeiten gezeigt werden
   */
  record Einzeln(long accountId) implements Personenauswahl {

    @Override
    public String alsParameter() {
      return Long.toString(accountId);
    }
  }
}
