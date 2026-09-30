package org.mwolff.fbcrm.angebot.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;

/**
 * Alles, was auf dem Beleg steht — die Vorlage des Satzes (Kriterium 15).
 *
 * <p>Die Reihenfolge der Angaben ist die des Kriteriums. Der Record kennt kein Angebot und keine
 * Uhr: Wer ihn fuellt, hat den Zustand schon geprueft und die Anschriften schon kopiert (R8), und
 * was hier steht, ist die Fassung, die der Kunde bekommt. Genau deshalb traegt er die Summe als
 * Wert und nicht als Verweis auf eine Rechnung — das abgelegte Dokument darf sich nicht aendern,
 * wenn sich spaeter eine Rechenregel aendert (Kriterium 14).
 *
 * @param absender Kopie der eigenen Angaben
 * @param empfaenger Kopie der Empfaengeranschrift
 * @param nummer die Angebotsnummer
 * @param angebotDatum Datum des Angebots
 * @param gueltigBis letzter Tag der Gueltigkeit
 * @param leistungsbeschreibung einleitender Text, oder {@code null}
 * @param positionen die Positionen in ihrer Reihenfolge
 * @param summe die Netto-Summe
 * @param zahlungsbedingungen Zahlungsbedingungen, oder {@code null}
 */
public record AngebotDruckdaten(
    Belegabsender absender,
    Belegempfaenger empfaenger,
    String nummer,
    LocalDate angebotDatum,
    LocalDate gueltigBis,
    @Nullable String leistungsbeschreibung,
    List<Angebotsposition> positionen,
    BigDecimal summe,
    @Nullable String zahlungsbedingungen) {

  /** Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public AngebotDruckdaten {
    positionen = List.copyOf(positionen);
  }
}
