package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Aendern eines Angebots — in jedem Status (Issue #127, Kriterium 5).
 *
 * <p>Geschrieben wird das Angebot als Ganzes: Datum, Ansprechpartner, Beschreibung und die
 * vollstaendige Positionsliste in der gewuenschten Reihenfolge (E8). Die Plaetze der Positionen
 * vergibt der Bestand daraus lueckenlos neu (E24). Die Firma und der Status bleiben, wie sie sind.
 *
 * <p>Der Ansprechpartner geht durch {@link Ansprechpartnerwahl}: Ein neu gewaehlter muss zur Firma
 * gehoeren und aktiv sein, der bisherige bleibt wählbar.
 *
 * <p><b>Die Positionskennungen werden hier geprueft</b> und nur hier (Plan #169, E2). Eine Position
 * mit Kennung sagt „dieselbe Position wie vorher"; gueltig sind darum ausschliesslich die Kennungen
 * der Positionen des geladenen Angebots, und jede hoechstens einmal. Alles andere ist {@link
 * PositionenNichtWaehlbar} — geprueft vor dem Schreiben, damit eine abgewiesene Aenderung nichts
 * hinterlaesst. Der Bestand darf sich danach darauf verlassen: Dort ist eine fremde Kennung nur
 * noch ein Programmierfehler.
 */
@Service
@Transactional
public class AngebotAendernUseCase {

  private final AngebotRepository angebote;
  private final Ansprechpartnerwahl wahl;
  private final Clock clock;

  AngebotAendernUseCase(
      final AngebotRepository angebote, final Ansprechpartnerwahl wahl, final Clock clock) {
    this.angebote = angebote;
    this.wahl = wahl;
    this.clock = clock;
  }

  /**
   * Aendert das Angebot und liefert es in seinem neuen Stand.
   *
   * @param angebotId Kennung des Angebots
   * @param daten die eingereichten Angaben samt vollstaendiger Positionsliste
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AnsprechpartnerNichtWaehlbar wenn der gewaehlte Ansprechpartner nicht zur Wahl steht
   * @throws PositionenNichtWaehlbar wenn eine Positionskennung nicht zu diesem Angebot gehoert oder
   *     zweimal eingereicht wurde
   */
  public Angebot aendere(final long angebotId, final AngebotDaten daten) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    wahl.pruefe(angebot.firmaId(), daten.ansprechpartnerId(), angebot.ansprechpartnerId());
    pruefeKennungen(daten.positionen(), angebot.positionen());
    return angebote.save(
        angebot.geaendert(
            daten.angebotDatum(),
            daten.ansprechpartnerId(),
            daten.beschreibung(),
            daten.positionen(),
            clock.instant()));
  }

  /*
   * Die eingereichten Kennungen gegen die des geladenen Angebots. Geprueft wird die ganze Liste,
   * bevor irgendetwas geschrieben wird; eine Position ohne Kennung ist neu und geht durch.
   */
  private static void pruefeKennungen(
      final List<Angebotsposition> eingereicht, final List<Angebotsposition> vorhanden) {
    final Set<Long> offen = new HashSet<>();
    for (final Angebotsposition position : vorhanden) {
      offen.add(position.id());
    }
    for (final Angebotsposition position : eingereicht) {
      final Long kennung = position.id();
      // remove statt contains: Eine zweimal eingereichte Kennung faellt beim zweiten Mal durch.
      if (kennung != null && !offen.remove(kennung)) {
        throw new PositionenNichtWaehlbar();
      }
    }
  }
}
