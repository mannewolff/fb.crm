package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.Map;
import java.util.Objects;

/**
 * Was ein Konto in der Monatsliste sieht: die geltende Auswahl und die Personen, deren Namen es an
 * den Zeilen lesen darf (Plan #426, E5, E10).
 *
 * <p>Beides entsteht in einem Zug in {@link Personenauskunft}, weil es an derselben Regel haengt:
 * Ein Employee sieht allein sich selbst, alle anderen sehen alle Konten.
 *
 * @param auswahl wessen Zeiten die Liste zeigt
 * @param personen die Personen, die das Konto sehen darf, nach Kennung
 */
record Personensicht(Personenauswahl auswahl, Map<Long, WaehlbarePerson> personen) {

  /** Nimmt die Personen als Kopie: Der Aufrufer darf seine Abbildung danach weiterverwenden. */
  Personensicht {
    personen = Map.copyOf(personen);
  }

  /*
   * Die Person ist da: Gelesen werden nur Eintraege der Auswahl, und jede Auswahl liegt in personen
   * — eine einzelne Person prueft Personenauskunft, „Alle" gibt es nur mit allen Konten, und der
   * Fremdschluessel haelt jeden Eintrag an einem Konto fest, das nie geloescht wird. requireNonNull
   * schreibt diese Zusage an der Nahtstelle hin.
   */
  WaehlbarePerson person(final long accountId) {
    return Objects.requireNonNull(personen.get(accountId));
  }
}
