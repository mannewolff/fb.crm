package org.mwolff.fbcrm.vorgang.application;

import org.jspecify.annotations.Nullable;

/**
 * Die Angaben eines Vorgangs, so wie ein Aufrufer sie einreicht — roh, ungeprueft, ungetrimmt.
 *
 * <p>Getrennt von {@link org.mwolff.fbcrm.vorgang.domain.Vorgang}, weil dort Kennung, Nummer,
 * Abschlussstand und Zeitstempel dazugehoeren: Die entscheidet nicht, wer einen Vorgang anlegt oder
 * aendert. Vor allem die Nummer — sie kommt aus dem Nummernkreis und nie aus der Maske (E3).
 *
 * @param titel Titel des Vorgangs; Pflicht (Kriterium 5)
 * @param firmaId Kennung der gewaehlten Firma; Pflicht (Kriterium 5)
 * @param ansprechpartnerId Kennung des gewaehlten Ansprechpartners, oder {@code null}
 */
public record VorgangDaten(String titel, long firmaId, @Nullable Long ansprechpartnerId) {

  /**
   * Dieselben Angaben, so wie sie in den Bestand gehoeren (E9).
   *
   * <p>Leerraum am Rand faellt weg — sonst gaebe es zwei Schreibweisen fuer denselben Titel, die
   * Sortierung, Suche und Anzeige beide kennen muessten.
   *
   * @throws IllegalArgumentException wenn vom Titel nur Leerraum uebrig bleibt. An der
   *     Schnittstelle weist {@code @NotBlank} dieselbe Eingabe schon ab; hier steht die Pruefung
   *     ein zweites Mal, weil die Transaktionsgrenze sich nicht darauf verlassen soll, wer sie
   *     aufruft.
   */
  public VorgangDaten normalisiert() {
    final String sauberer = titel.strip();
    if (sauberer.isEmpty()) {
      throw new IllegalArgumentException("Einen Vorgang ohne Titel gibt es nicht.");
    }
    return new VorgangDaten(sauberer, firmaId, ansprechpartnerId);
  }
}
