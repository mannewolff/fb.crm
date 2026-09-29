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
 * Die Angebotsliste einer Firma (Kriterium 20).
 *
 * <p><b>Die Reihenfolge aus E25:</b> Entwuerfe stehen oben, innerhalb jeder Gruppe zaehlt der
 * Anlagezeitpunkt absteigend, und bei gleichem Zeitpunkt entscheidet die hoehere Kennung — damit
 * zwei Aufrufe dieselbe Liste liefern. Weder das Angebotsdatum noch die Nummer taugen als
 * Schluessel: Das Datum ist nach Kriterium 3 frei setzbar, und Entwuerfe haben keine Nummer.
 *
 * <p>Sortiert wird hier und nicht im Bestand: Die Ordnung ist eine Aussage der Ansicht und keine
 * Eigenschaft der Zeilen — der Bestand liefert den Inhalt.
 *
 * <p>Eine unbekannte Firma ist hier kein Fehler, sondern eine Firma ohne Angebot: Ob es sie gibt,
 * beantwortet ihre eigene Detailansicht.
 */
@Service
@Transactional(readOnly = true)
public class AngeboteDerFirmaUseCase {

  private static final Comparator<Angebot> REIHENFOLGE =
      Comparator.<Angebot>comparingInt(AngeboteDerFirmaUseCase::gruppe)
          .thenComparing(Angebot::createdAt, Comparator.reverseOrder())
          .thenComparing(Angebot::requireId, Comparator.reverseOrder());

  private final AngebotRepository bestand;
  private final Clock clock;

  public AngeboteDerFirmaUseCase(final AngebotRepository bestand, final Clock clock) {
    this.bestand = bestand;
    this.clock = clock;
  }

  /**
   * Die Angebote einer Firma in der Reihenfolge aus Kriterium 20.
   *
   * @param firmaId Kennung der Firma
   */
  public List<AngebotAnsicht> angebote(final long firmaId) {
    return AngebotAnsicht.of(
        bestand.findByFirma(firmaId).stream().sorted(REIHENFOLGE).toList(), clock);
  }

  /* Entwuerfe zuerst: die Gruppe 0, alles Festgeschriebene die Gruppe 1. */
  private static int gruppe(final Angebot angebot) {
    return angebot.zustand() == Angebotszustand.ENTWURF ? 0 : 1;
  }
}
