package org.mwolff.fbcrm.firma.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Laenderliste;

/**
 * Die Angaben einer Firma, so wie ein Aufrufer sie einreicht — roh, ungeprueft, ungetrimmt.
 *
 * <p>Getrennt von {@link org.mwolff.fbcrm.firma.domain.Firma}, weil dort Kennung, Stilllegungsstand
 * und Zeitstempel dazugehoeren: Die entscheidet nicht, wer eine Firma anlegt oder aendert.
 *
 * @param name Name der Firma; Pflicht
 * @param anschrift Postanschrift; Pflicht ist allein das Land, ein Kuerzel der {@link Laenderliste}
 *     (Plan #461, E11)
 * @param steuernummer Steuernummer, oder {@code null}
 * @param umsatzsteuerId Umsatzsteuer-Identifikationsnummer, oder {@code null}
 */
public record FirmaDaten(
    String name,
    Anschrift anschrift,
    @Nullable String steuernummer,
    @Nullable String umsatzsteuerId) {

  /**
   * Dieselben Angaben, so wie sie in den Bestand gehoeren (E9).
   *
   * <p>Leerraum am Rand faellt bei jeder Eingabe weg, und ein optionales Feld, von dem danach
   * nichts uebrig ist, wird {@code null} statt Leerstring: Sonst gaebe es zwei Schreibweisen fuer
   * „nicht angegeben", die Sortierung, Suche und Anzeige beide kennen muessten.
   *
   * @throws IllegalArgumentException wenn vom Namen nur Leerraum uebrig bleibt oder das Land kein
   *     Kuerzel der Laenderliste ist. An der Schnittstelle weisen {@code @NotBlank} und {@code
   *     LaenderkuerzelConstraint} dieselbe Eingabe schon ab; hier steht die Pruefung ein zweites
   *     Mal, weil die Transaktionsgrenze sich nicht darauf verlassen soll, wer sie aufruft.
   */
  public FirmaDaten normalisiert() {
    final String sauberer = name.strip();
    if (sauberer.isEmpty()) {
      throw new IllegalArgumentException("Eine Firma ohne Namen gibt es nicht.");
    }
    final String land = ohneLeerraum(anschrift.land());
    if (!Laenderliste.bekannt(land)) {
      throw new IllegalArgumentException("Das Land muss ein Kuerzel der Laenderliste sein.");
    }
    return new FirmaDaten(
        sauberer,
        new Anschrift(
            ohneLeerraum(anschrift.strasse()),
            ohneLeerraum(anschrift.plz()),
            ohneLeerraum(anschrift.ort()),
            land),
        ohneLeerraum(steuernummer),
        ohneLeerraum(umsatzsteuerId));
  }

  private static @Nullable String ohneLeerraum(final @Nullable String wert) {
    if (wert == null) {
      return null;
    }
    final String sauberer = wert.strip();
    return sauberer.isEmpty() ? null : sauberer;
  }
}
