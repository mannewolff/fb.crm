package org.mwolff.fbcrm.rechnung.application;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Die eingereichten Angaben zu einem Rechnungsentwurf (#160, Kriterien 4, 9, 10).
 *
 * <p>Geschrieben wird der Entwurf als Ganzes: Datum, Leistungszeitraum und die vollstaendige Liste
 * der Angaben — dieselbe Form wie am Angebot. Was nicht in der Liste steht, steht danach nicht auf
 * der Rechnung.
 *
 * @param rechnungDatum Datum der Rechnung
 * @param leistungszeitraum Zeitraum der Leistung als Text, oder {@code null}
 * @param angaben je Angebotsposition hoechstens eine Angabe
 * @param version der Stand, auf dem die Aenderung beruht — vorgelegt mit {@code If-Match} (Plan
 *     #415, E2)
 * @param geaendertVon Kennung des handelnden Kontos; es steht danach als Urheber an der Rechnung
 *     (E14)
 */
public record RechnungDaten(
    LocalDate rechnungDatum,
    @Nullable String leistungszeitraum,
    List<Abrechnungsangabe> angaben,
    long version,
    long geaendertVon) {

  /** Nimmt die Angaben als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public RechnungDaten {
    angaben = List.copyOf(angaben);
  }
}
