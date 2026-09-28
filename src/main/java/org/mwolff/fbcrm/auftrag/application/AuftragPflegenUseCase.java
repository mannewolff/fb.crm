package org.mwolff.fbcrm.auftrag.application;

import java.time.Clock;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.vorgang.application.EreignisVermerkenUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Pflegen eines Auftrags (Kriterien 7, 8; F3, F6).
 *
 * <p><b>Ein Schreibweg fuer alle vier aenderbaren Angaben</b> (Plan E8) und keine eigenen Wege je
 * Zielzustand: Kriterium 7 nennt Auftragsdatum, Kundenbestellnummer, Leistungszeitraum und Status
 * in einem Atemzug und laesst den Status in jede Richtung frei setzen (F6). Die Positionen kommen
 * in {@link AuftragPflegedaten} gar nicht vor und lassen sich darum nicht anfassen — genau so, wie
 * Kriterium 7 es verlangt.
 *
 * <p><b>Das Ereignis nur beim echten Wechsel</b> (Plan E9): Ein Aufruf, der nur die Bestellnummer
 * aendert, schreibt keine Zeile in die Historie. Eine Historie, in der jeder Tastendruck steht,
 * verbirgt das, wofuer sie da ist.
 *
 * <p><b>Keine Sperre am abgeschlossenen Vorgang</b> (Plan E14, Kriterium 11): Die Sperre reicht nur
 * bis zum Anlegen. Dass dieser Anwendungsfall den Vorgang gar nicht kennt, ist die einfachste Form
 * dieser Zusage — dieselbe Bauart wie {@code angebot.application.AngebotEntwurfAendernUseCase}.
 *
 * <p>Die Nummer des Quell-Angebots wird nur geholt, um die Antwort zu fuellen (Kriterium 3): Sie
 * steht nicht am Auftrag, und eine Kennung zeigt kein Wort.
 */
@Service
@Transactional
public class AuftragPflegenUseCase {

  private final AuftragRepository auftraege;
  private final AngebotRepository angebote;
  private final EreignisVermerkenUseCase ereignisse;
  private final Clock clock;

  public AuftragPflegenUseCase(
      final AuftragRepository auftraege,
      final AngebotRepository angebote,
      final EreignisVermerkenUseCase ereignisse,
      final Clock clock) {
    this.auftraege = auftraege;
    this.angebote = angebote;
    this.ereignisse = ereignisse;
    this.clock = clock;
  }

  /**
   * Schreibt die vier aenderbaren Angaben fort und liefert den Auftrag in seinem neuen Stand.
   *
   * @param auftragId Kennung des Auftrags
   * @param daten die vier Angaben aus Kriterium 7
   * @throws AuftragNichtGefunden wenn es den Auftrag nicht gibt
   * @throws AngebotNichtGefunden wenn es das Quell-Angebot nicht gibt
   */
  public AuftragAnsicht pflege(final long auftragId, final AuftragPflegedaten daten) {
    final Auftrag vorhanden = auftraege.findById(auftragId).orElseThrow(AuftragNichtGefunden::new);
    final Auftrag gepflegt =
        auftraege.save(
            vorhanden.gepflegt(
                daten.auftragDatum(),
                daten.kundenbestellnummer(),
                daten.leistungAb(),
                daten.leistungBis(),
                daten.status(),
                clock.instant()));
    if (vorhanden.status() != daten.status()) {
      ereignisse.vermerken(
          gepflegt.vorgangId(), Auftragsereignis.statusGesetzt(gepflegt.nummer(), daten.status()));
    }
    return new AuftragAnsicht(
        gepflegt,
        angebote.findById(gepflegt.angebotId()).orElseThrow(AngebotNichtGefunden::new).nummer());
  }
}
