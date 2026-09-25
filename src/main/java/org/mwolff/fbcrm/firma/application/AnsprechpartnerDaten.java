package org.mwolff.fbcrm.firma.application;

import org.jspecify.annotations.Nullable;

/**
 * Die Angaben eines Ansprechpartners, so wie ein Aufrufer sie einreicht — roh, ungeprueft,
 * ungetrimmt.
 *
 * <p>Getrennt von {@link org.mwolff.fbcrm.firma.domain.Ansprechpartner}, weil dort Kennung, Firma,
 * Stilllegungsstand und Zeitstempel dazugehoeren. Vor allem die Firma: Sie kommt aus dem Pfad und
 * nie aus den Angaben der Maske — ein Feld dafuer gaebe es hier nicht, und damit gibt es auch
 * keinen Weg, einen Ansprechpartner umzuhaengen (E7, Kriterium 12).
 *
 * @param vorname Vorname, oder {@code null}
 * @param nachname Nachname; die einzige Pflichtangabe
 * @param rolle Rolle oder Funktion in der Firma, oder {@code null}
 * @param email E-Mail-Adresse, oder {@code null}
 * @param telefonFestnetz Festnetznummer, oder {@code null}
 * @param telefonMobil Mobilnummer, oder {@code null}
 */
public record AnsprechpartnerDaten(
    @Nullable String vorname,
    String nachname,
    @Nullable String rolle,
    @Nullable String email,
    @Nullable String telefonFestnetz,
    @Nullable String telefonMobil) {

  /**
   * Dieselben Angaben, so wie sie in den Bestand gehoeren (E9).
   *
   * <p>Leerraum am Rand faellt bei jeder Eingabe weg, und ein optionales Feld, von dem danach
   * nichts uebrig ist, wird {@code null} statt Leerstring — dieselbe Regel wie bei {@link
   * FirmaDaten}.
   *
   * @throws IllegalArgumentException wenn vom Nachnamen nur Leerraum uebrig bleibt. An der
   *     Schnittstelle weist {@code @NotBlank} dieselbe Eingabe schon ab; hier steht die Pruefung
   *     ein zweites Mal, weil die Transaktionsgrenze sich nicht darauf verlassen soll, wer sie
   *     aufruft.
   */
  public AnsprechpartnerDaten normalisiert() {
    final String sauberer = nachname.strip();
    if (sauberer.isEmpty()) {
      throw new IllegalArgumentException("Einen Ansprechpartner ohne Nachnamen gibt es nicht.");
    }
    return new AnsprechpartnerDaten(
        ohneLeerraum(vorname),
        sauberer,
        ohneLeerraum(rolle),
        ohneLeerraum(email),
        ohneLeerraum(telefonFestnetz),
        ohneLeerraum(telefonMobil));
  }

  private static @Nullable String ohneLeerraum(final @Nullable String wert) {
    if (wert == null) {
      return null;
    }
    final String sauberer = wert.strip();
    return sauberer.isEmpty() ? null : sauberer;
  }
}
