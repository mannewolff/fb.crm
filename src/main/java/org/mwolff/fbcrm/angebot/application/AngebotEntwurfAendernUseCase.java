package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Fortschreiben eines Entwurfs (Kriterien 6, 13).
 *
 * <p>Geschrieben wird der Entwurf als Ganzes: Texte, Gueltigkeit und die vollstaendige
 * Positionsliste in der gewuenschten Reihenfolge (E8). Die Plaetze der Positionen vergibt der
 * Bestand daraus lueckenlos neu (E24) — Hinzufuegen, Aendern, Loeschen und Verschieben fallen aus
 * der Reihenfolge der Liste heraus, und das Schreiben bleibt eine Transaktion mit einem gueltigen
 * Zwischenstand.
 *
 * <p>Ob der Uebergang zulaessig ist, entscheidet {@link Angebot#entwurfGeaendert} — in der Domaene
 * und nicht hier, damit kein Weg an der Zustandsmaschine vorbeifuehrt. Die Ausnahme fliegt, bevor
 * gespeichert wird; ein festgeschriebenes Angebot wird also nicht einmal angefasst.
 */
@Service
@Transactional
public class AngebotEntwurfAendernUseCase {

  private final AngebotRepository angebote;
  private final Clock clock;

  public AngebotEntwurfAendernUseCase(final AngebotRepository angebote, final Clock clock) {
    this.angebote = angebote;
    this.clock = clock;
  }

  /**
   * Schreibt den Entwurf fort und liefert ihn in seinem neuen Stand.
   *
   * @param angebotId Kennung des Angebots
   * @param daten die eingereichten Angaben samt vollstaendiger Positionsliste
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotNichtAenderbar wenn das Angebot kein Entwurf mehr ist
   */
  public AngebotAnsicht aendere(final long angebotId, final EntwurfDaten daten) {
    final Angebot entwurf = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    final Angebot geaendert =
        entwurf.entwurfGeaendert(
            daten.gueltigBis(),
            daten.leistungsbeschreibung(),
            daten.zahlungsbedingungen(),
            daten.positionen(),
            clock.instant());
    return AngebotAnsicht.of(angebote.save(geaendert), clock);
  }
}
