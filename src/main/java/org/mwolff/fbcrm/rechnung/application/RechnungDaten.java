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
 */
public record RechnungDaten(
    LocalDate rechnungDatum, @Nullable String leistungszeitraum, List<Abrechnungsangabe> angaben) {

  /** Nimmt die Angaben als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public RechnungDaten {
    angaben = List.copyOf(angaben);
  }
}
