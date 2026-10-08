package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Aendern einer nachgetragenen Rechnung — in jedem Zustand (#254, Kriterium 10; Plan #259).
 *
 * <p>Es gelten dieselben Pruefungen wie beim Nachtragen, mit zwei Unterschieden:
 *
 * <ul>
 *   <li><b>Die Datumsgrenze gilt nur, wenn das Datum sich aendert</b> (E8). Bleibt es, wie es ist,
 *       laesst sich die Rechnung auch nach dem Jahreswechsel noch aendern.
 *   <li><b>Die eigene Nummer bleibt erlaubt</b>: gefragt wird {@link
 *       Rechnungsnummern#vergebenVonAnderer}, bei dem die eigene Zeile nicht mitzaehlt.
 * </ul>
 *
 * <p>Zustand und Original bleiben unberuehrt; fuer beide gibt es eigene Anwendungsfaelle.
 */
@Service
@Transactional
public class NachtragAendernUseCase {

  private final NachgetrageneRechnungRepository nachgetragene;
  private final FirmaRepository firmen;
  private final Rechnungsnummern rechnungsnummern;
  private final Clock clock;

  NachtragAendernUseCase(
      final NachgetrageneRechnungRepository nachgetragene,
      final FirmaRepository firmen,
      final Rechnungsnummern rechnungsnummern,
      final Clock clock) {
    this.nachgetragene = nachgetragene;
    this.firmen = firmen;
    this.rechnungsnummern = rechnungsnummern;
    this.clock = clock;
  }

  /**
   * Aendert die Eckdaten der Rechnung und liefert sie in ihrem neuen Stand.
   *
   * @param id Kennung der nachgetragenen Rechnung
   * @param daten die eingereichten Eckdaten, die Nummer schon getrimmt
   * @throws NachtragNichtGefunden wenn es die Rechnung nicht gibt
   * @throws FirmaNichtGefunden wenn es die Firma nicht gibt
   * @throws RechnungsdatumAusserhalb wenn ein geaendertes Datum nicht im laufenden Jahr bis heute
   *     liegt
   * @throws NummerSchonVergeben wenn eine andere Rechnung diese Nummer schon traegt
   * @throws BetraegePassenNicht wenn der Bruttobetrag unter dem Nettobetrag liegt
   */
  public NachgetrageneRechnung aendere(final long id, final Nachtragsdaten daten) {
    final NachgetrageneRechnung rechnung =
        nachgetragene.findById(id).orElseThrow(NachtragNichtGefunden::new);
    if (firmen.findById(daten.firmaId()).isEmpty()) {
      throw new FirmaNichtGefunden();
    }
    if (!daten.rechnungDatum().equals(rechnung.rechnungDatum())) {
      RechnungsdatumAusserhalb.pruefe(daten.rechnungDatum(), clock);
    }
    if (rechnungsnummern.vergebenVonAnderer(daten.nummer(), id)) {
      throw new NummerSchonVergeben();
    }
    BetraegePassenNicht.pruefe(daten.netto(), daten.brutto());
    return nachgetragene.save(
        rechnung.geaendert(
            daten.firmaId(),
            daten.nummer(),
            daten.rechnungDatum(),
            daten.netto(),
            daten.brutto(),
            clock.instant()));
  }
}
