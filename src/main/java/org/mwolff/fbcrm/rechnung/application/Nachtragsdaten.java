package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Die Eckdaten einer nachgetragenen Rechnung, so wie ein Aufrufer sie einreicht (#254, Kriterium 2;
 * Plan #259, E10).
 *
 * <p>Getrennt von {@link org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung}, weil dort
 * Kennung, Zustand, Original und Zeitstempel dazugehoeren: Die entscheidet nicht, wer eine Rechnung
 * nachtraegt oder aendert.
 *
 * <p><b>Die Nummer wird hier getrimmt</b> und nur hier — genau einmal, vor Pruefung und Speichern.
 * Sonst fragte die Nummernpruefung nach „ RE-1 " und gespeichert wuerde „RE-1", oder umgekehrt.
 *
 * @param firmaId Kennung der Firma, an die die Rechnung ging
 * @param nummer die frei erfasste Rechnungsnummer; Leerraum am Rand faellt weg
 * @param rechnungDatum Datum der Rechnung
 * @param netto der Nettobetrag, wie erfasst
 * @param brutto der Bruttobetrag, wie erfasst
 */
public record Nachtragsdaten(
    long firmaId, String nummer, LocalDate rechnungDatum, BigDecimal netto, BigDecimal brutto) {

  /** Entfernt den Leerraum am Rand der Nummer. */
  public Nachtragsdaten {
    nummer = nummer.strip();
  }
}
