package org.mwolff.fbcrm.vorgang.web;

import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.Firma;

/**
 * Die Zuordnung eines Vorgangs — eine Firma oder ein Ansprechpartner — so, wie die Detailansicht
 * sie zeigt (Kriterien 9, 23).
 *
 * <p>Drei Angaben, und jede hat ihren Grund: der Name fuer die Anzeige, die Kennung fuer den Weg
 * auf die Detailansicht der Firma, und {@code aktiv} fuer die Kennzeichnung „stillgelegt". Ohne den
 * Stand waere eine stillgelegte Zuordnung von einer aktiven nicht zu unterscheiden.
 *
 * @param id technische Id
 * @param name Name der Firma beziehungsweise voller Name des Ansprechpartners
 * @param aktiv {@code false}, solange die Zuordnung stillgelegt ist
 */
public record ZuordnungResponse(long id, String name, boolean aktiv) {

  /** Die Sicht der Oberflaeche auf die zugeordnete Firma. */
  static ZuordnungResponse of(final Firma firma) {
    return new ZuordnungResponse(firma.requireId(), firma.name(), firma.aktiv());
  }

  /**
   * Die Sicht der Oberflaeche auf den zugeordneten Ansprechpartner.
   *
   * <p>Vor- und Nachname stehen zusammengesetzt und nicht getrennt: Die Ansicht zeigt einen Namen,
   * und der Vorname darf fehlen — ihn dann als Feld mitzuschicken hiesse, die Zusammensetzung in
   * jeder Ansicht noch einmal zu entscheiden.
   */
  static ZuordnungResponse of(final Ansprechpartner ansprechpartner) {
    final String vorname = ansprechpartner.vorname();
    final String nachname = ansprechpartner.nachname();
    return new ZuordnungResponse(
        ansprechpartner.requireId(),
        vorname == null ? nachname : vorname + " " + nachname,
        ansprechpartner.aktiv());
  }
}
