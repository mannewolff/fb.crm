package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Nachtragen einer Rechnung, die ausserhalb von fb.crm geschrieben wurde (#254, Kriterien 2, 3,
 * 4, 8; Plan #259).
 *
 * <p>Die Firma ist Pflicht und darf stillgelegt sein — die Rechnung ging an sie, als sie noch aktiv
 * war. Das Datum liegt zwischen dem 1. Januar des laufenden Jahres und heute, gerechnet gegen die
 * injizierte Uhr in der Geschaeftszone (E7). Die Nummer darf keine andere Rechnung tragen, gleich
 * ob geschrieben oder nachgetragen; gefragt wird {@link Rechnungsnummern}, die eine Stelle dafuer.
 *
 * <p><b>Brutto unter Netto wird hier gemeldet</b>, bevor das Aggregat seine Invariante wirft (E9):
 * Der Anwender soll die Meldung am Bruttobetrag lesen und keinen Programmierfehler bekommen.
 *
 * <p>Die Rechnung entsteht sofort im Zustand {@code GESTELLT} (Kriterium 8) — einen Entwurf gibt es
 * bei ihr nicht.
 */
@Service
@Transactional
public class NachtragAnlegenUseCase {

  private final NachgetrageneRechnungRepository nachgetragene;
  private final FirmaRepository firmen;
  private final Rechnungsnummern rechnungsnummern;
  private final Clock clock;

  NachtragAnlegenUseCase(
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
   * Traegt eine Rechnung nach und liefert sie mit ihrer Kennung.
   *
   * @param daten die eingereichten Eckdaten, die Nummer schon getrimmt
   * @throws FirmaNichtGefunden wenn es die Firma nicht gibt
   * @throws RechnungsdatumAusserhalb wenn das Datum nicht im laufenden Jahr bis heute liegt
   * @throws NummerSchonVergeben wenn eine andere Rechnung diese Nummer schon traegt
   * @throws BetraegePassenNicht wenn der Bruttobetrag unter dem Nettobetrag liegt
   */
  public NachgetrageneRechnung anlege(final Nachtragsdaten daten) {
    if (firmen.findById(daten.firmaId()).isEmpty()) {
      throw new FirmaNichtGefunden();
    }
    RechnungsdatumAusserhalb.pruefe(daten.rechnungDatum(), clock);
    if (rechnungsnummern.vergeben(daten.nummer())) {
      throw new NummerSchonVergeben();
    }
    BetraegePassenNicht.pruefe(daten.netto(), daten.brutto());
    final Instant jetzt = clock.instant();
    return nachgetragene.save(
        new NachgetrageneRechnung(
            null,
            daten.firmaId(),
            daten.nummer(),
            daten.rechnungDatum(),
            daten.netto(),
            daten.brutto(),
            Rechnungszustand.GESTELLT,
            null,
            jetzt,
            jetzt));
  }
}
