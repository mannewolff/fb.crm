package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Positionen, auf die jetzt Arbeitszeit gebucht werden darf (Plan #194, A15).
 *
 * <p>Das ist die Auswahlliste des Dialogs, und sie steht fuer sich: ohne Parameter, weil der Dialog
 * jede buchbare Position aller zugesagten Angebote anbietet. Welche das sind, sagt {@link
 * Buchbarkeit#buchungZulaessig} und nur sie — nach Aufwand, in Stunden, und das Angebot bestellt
 * oder erledigt (Issue #193, Antworten 2, 3 und 5).
 *
 * <p><b>Die Reihenfolge ist die der Gruppierung</b> (A15): Firma alphabetisch, darin das neueste
 * Angebot zuerst, darin die Positionen in der Reihenfolge des Angebots. Die Oberflaeche kann die
 * Liste damit in einem Durchgang in Gruppen zerlegen, ohne selbst zu sortieren.
 *
 * <p><b>Ein Zug in den Bestand der Angebote und einer fuer die Namen</b> ({@link
 * Buchungspositionen}); ohne buchbare Position wird nach keinem Namen gefragt — darum steht hier
 * keine eigene Abkuerzung fuer den leeren Fall: Die Anreicherung fragt von sich aus nicht, wenn
 * nichts zu beschreiben ist, und ein zweiter Riegel davor waere eine Verzweigung ohne Wirkung.
 * Gefiltert wird in der Anwendungsschicht und nicht in der Abfrage: Die Regel, was buchbar ist,
 * steht an einer Stelle, und {@code findAlle} kennt nur einen Status je Aufruf.
 */
@Service
@Transactional(readOnly = true)
public class BuchbarePositionenUseCase {

  /**
   * Firma alphabetisch, darin das neueste Angebot zuerst, bei gleichem Datum die hoehere Kennung.
   */
  private static final Comparator<Buchungsposition> AUSWAHLREIHENFOLGE =
      Comparator.comparing(Buchungsposition::firmaName)
          .thenComparing(Buchungsposition::angebotDatum, Comparator.reverseOrder())
          .thenComparing(Buchungsposition::angebotId, Comparator.reverseOrder());

  private final AngebotRepository angebote;
  private final FirmaRepository firmen;

  BuchbarePositionenUseCase(final AngebotRepository angebote, final FirmaRepository firmen) {
    this.angebote = angebote;
    this.firmen = firmen;
  }

  /**
   * Alle Positionen mit zulaessiger Buchung, gruppierbar nach Firma und Angebot.
   *
   * @throws FirmaNichtGefunden wenn die Firma eines beteiligten Angebots fehlt
   */
  public List<Buchungsposition> positionen() {
    final List<Angebot> gefunden = angebote.findAlle(Optional.empty());
    /*
     * Sortiert wird am Ergebnis und nicht an den Angeboten: Der Name der Firma entscheidet die
     * Ordnung, und er entsteht erst in der Anreicherung. Der Sortierlauf ist stabil, also behalten
     * die Positionen eines Angebots dabei ihre Reihenfolge.
     */
    return Buchungspositionen.beschreibe(gefunden, kennungenMitZulaessigerBuchung(gefunden), firmen)
        .values()
        .stream()
        .sorted(AUSWAHLREIHENFOLGE)
        .toList();
  }

  private static Set<Long> kennungenMitZulaessigerBuchung(final List<Angebot> gefunden) {
    return gefunden.stream()
        .flatMap(
            angebot ->
                angebot.positionen().stream()
                    .filter(position -> Buchbarkeit.buchungZulaessig(angebot, position))
                    .map(Angebotsposition::requireId))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
