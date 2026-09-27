package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Angebotsliste eines Vorgangs (Kriterium 20).
 *
 * <p><b>Die Reihenfolge aus E25:</b> Entwuerfe stehen oben, innerhalb jeder Gruppe zaehlt der
 * Anlagezeitpunkt absteigend, und bei gleichem Zeitpunkt entscheidet die hoehere Kennung — damit
 * zwei Aufrufe dieselbe Liste liefern. Weder das Angebotsdatum noch die Nummer taugen als
 * Schluessel: Das Datum ist nach Kriterium 3 frei setzbar, und Entwuerfe haben keine Nummer.
 *
 * <p>Sortiert wird hier und nicht im Bestand: Die Ordnung ist eine Aussage der Ansicht und keine
 * Eigenschaft der Zeilen — der Bestand liefert den Inhalt.
 *
 * <p>Ein unbekannter Vorgang ist hier kein Fehler, sondern ein Vorgang ohne Angebot; dieselbe
 * Abwaegung wie bei {@code VorgaengeDerFirmaUseCase}: Ob es ihn gibt, beantwortet seine eigene
 * Detailansicht.
 */
@Service
@Transactional(readOnly = true)
public class AngeboteDesVorgangsUseCase {

  private static final Comparator<Angebot> REIHENFOLGE =
      Comparator.<Angebot>comparingInt(AngeboteDesVorgangsUseCase::gruppe)
          .thenComparing(Angebot::createdAt, Comparator.reverseOrder())
          .thenComparing(Angebot::requireId, Comparator.reverseOrder());

  private final AngebotRepository bestand;
  private final Clock clock;

  public AngeboteDesVorgangsUseCase(final AngebotRepository bestand, final Clock clock) {
    this.bestand = bestand;
    this.clock = clock;
  }

  /**
   * Die Angebote eines Vorgangs in der Reihenfolge aus Kriterium 20.
   *
   * @param vorgangId Kennung des Vorgangs
   */
  public List<AngebotAnsicht> angebote(final long vorgangId) {
    return AngebotAnsicht.of(
        bestand.findByVorgang(vorgangId).stream().sorted(REIHENFOLGE).toList(), clock);
  }

  /* Entwuerfe zuerst: die Gruppe 0, alles Festgeschriebene die Gruppe 1. */
  private static int gruppe(final Angebot angebot) {
    return angebot.zustand() == Angebotszustand.ENTWURF ? 0 : 1;
  }
}
