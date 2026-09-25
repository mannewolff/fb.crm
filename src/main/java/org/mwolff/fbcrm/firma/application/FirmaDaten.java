package org.mwolff.fbcrm.firma.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.domain.Anschrift;

/**
 * Die Angaben einer Firma, so wie ein Aufrufer sie einreicht — roh, ungeprueft, ungetrimmt.
 *
 * <p>Getrennt von {@link org.mwolff.fbcrm.firma.domain.Firma}, weil dort Kennung, Stilllegungsstand
 * und Zeitstempel dazugehoeren: Die entscheidet nicht, wer eine Firma anlegt oder aendert.
 *
 * @param name Name der Firma; die einzige Pflichtangabe
 * @param anschrift Postanschrift; jede ihrer Angaben darf fehlen
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
   * @throws IllegalArgumentException wenn vom Namen nur Leerraum uebrig bleibt. An der
   *     Schnittstelle weist {@code @NotBlank} dieselbe Eingabe schon ab; hier steht die Pruefung
   *     ein zweites Mal, weil die Transaktionsgrenze sich nicht darauf verlassen soll, wer sie
   *     aufruft.
   */
  public FirmaDaten normalisiert() {
    final String sauberer = name.strip();
    if (sauberer.isEmpty()) {
      throw new IllegalArgumentException("Eine Firma ohne Namen gibt es nicht.");
    }
    return new FirmaDaten(
        sauberer,
        new Anschrift(
            ohneLeerraum(anschrift.strasse()),
            ohneLeerraum(anschrift.plz()),
            ohneLeerraum(anschrift.ort()),
            ohneLeerraum(anschrift.land())),
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
