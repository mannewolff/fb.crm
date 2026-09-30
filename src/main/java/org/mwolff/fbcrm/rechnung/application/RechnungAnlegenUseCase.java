package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.rechnung.domain.Abrechnungsstand;
import org.mwolff.fbcrm.rechnung.domain.Positionsstand;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Anlegen eines Rechnungsentwurfs zu einem Angebot (Plan #169, E5).
 *
 * <p><b>Die Wahl des Angebots legt den Entwurf sofort an</b> und nicht erst das Speichern der
 * Maske: Erst der gespeicherte Entwurf zaehlt bei „bereits abgerechnet" mit (#160, Kriterium 6),
 * und erst er haelt den Einzelpreis fest, der beim Anlegen galt (Kriterium 9). Wird nichts daraus,
 * loescht ihn der Anwender wieder (Kriterium 12).
 *
 * <p><b>Die Vorbelegung.</b> Jede Position, an der etwas offen ist, kommt mit ihrer offenen Menge
 * hinein; Text, Einheit und Einzelpreis sind Kopien aus dem Angebot von jetzt. Das Rechnungsdatum
 * ist der heutige Tag in der Geschaeftszone und nicht in UTC (E12), und der Leistungszeitraum ist
 * der laufende Monat als Text — zwischen 22:00 UTC und Mitternacht deutscher Zeit nennen beide
 * Uhren verschiedene Tage und am Monatsende verschiedene Monate.
 *
 * <p>Abgewiesen wird vor allem anderen: ein Angebot vor {@code BESTELLT} und ein Angebot, an dem
 * nichts offen ist. Beides sagt {@link AngebotNichtAbrechenbar}; der Server entscheidet, nicht die
 * Auswahlliste der Oberflaeche.
 */
@Service
@Transactional
public class RechnungAnlegenUseCase {

  /** Der laufende Monat als Text, so wie ihn Kriterium 10 zeigt: „September 2026". */
  private static final DateTimeFormatter MONAT =
      DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN);

  private final AngebotRepository angebote;
  private final RechnungRepository rechnungen;
  private final Clock clock;

  RechnungAnlegenUseCase(
      final AngebotRepository angebote, final RechnungRepository rechnungen, final Clock clock) {
    this.angebote = angebote;
    this.rechnungen = rechnungen;
    this.clock = clock;
  }

  /**
   * Legt zu einem Angebot einen Rechnungsentwurf an und liefert ihn mit seiner Kennung.
   *
   * @param angebotId Kennung des Angebots, das abgerechnet wird
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotNichtAbrechenbar wenn das Angebot vor {@code BESTELLT} steht oder an keiner
   *     seiner Positionen noch etwas offen ist
   */
  public Rechnung anlegen(final long angebotId) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    if (!Abrechenbarkeit.STATUS.contains(angebot.status())) {
      throw new AngebotNichtAbrechenbar();
    }
    final Abrechnungsstand stand =
        Abrechnungsstand.fuer(angebot.positionen(), rechnungen.findByAngebot(angebotId));
    if (!stand.etwasOffen()) {
      throw new AngebotNichtAbrechenbar();
    }
    final Instant jetzt = clock.instant();
    final LocalDate heute = LocalDate.now(clock.withZone(Geschaeftszone.ZONE));
    return rechnungen.save(
        new Rechnung(
            null,
            angebotId,
            Rechnungszustand.ENTWURF,
            heute,
            MONAT.format(heute),
            vorbelegung(stand),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            jetzt,
            jetzt));
  }

  private static List<Rechnungsposition> vorbelegung(final Abrechnungsstand stand) {
    return stand.positionen().stream()
        .filter(Positionsstand::offenesVorhanden)
        .map(
            positionsstand ->
                new Rechnungsposition(
                    positionsstand.position().requireId(),
                    positionsstand.position().bezeichnung(),
                    positionsstand.offen(),
                    positionsstand.position().einheit(),
                    positionsstand.position().einzelpreis()))
        .toList();
  }
}
