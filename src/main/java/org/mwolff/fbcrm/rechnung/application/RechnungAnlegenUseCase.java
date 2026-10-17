package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.arbeitszeit.application.Arbeitszeitauskunft;
import org.mwolff.fbcrm.arbeitszeit.application.Buchbarkeit;
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
 * Das Anlegen eines Rechnungsentwurfs zu einem Angebot (Plan #169, E5; Plan #194, A10, A11).
 *
 * <p><b>Die Wahl des Angebots legt den Entwurf sofort an</b> und nicht erst das Speichern der
 * Maske: Erst der gespeicherte Entwurf zaehlt bei „bereits abgerechnet" mit (#160, Kriterium 6),
 * und erst er haelt den Einzelpreis fest, der beim Anlegen galt (Kriterium 9). Wird nichts daraus,
 * loescht ihn der Anwender wieder (Kriterium 12).
 *
 * <p><b>Die Vorbelegung ohne Monat.</b> Jede Position, an der etwas offen ist, kommt mit ihrer
 * offenen Menge hinein; Text, Einheit und Einzelpreis sind Kopien aus dem Angebot von jetzt. Das
 * Rechnungsdatum ist der heutige Tag in der Geschaeftszone und nicht in UTC (E12), und der
 * Leistungszeitraum ist der laufende Monat als Text — zwischen 22:00 UTC und Mitternacht deutscher
 * Zeit nennen beide Uhren verschiedene Tage und am Monatsende verschiedene Monate.
 *
 * <p><b>Die Vorbelegung mit einem Monat</b> (Issue #193, Kriterien 9 bis 12): Jede buchbare
 * Position traegt die Summe der in diesem Monat erfassten Stunden, <b>auch ueber ihre offene Menge
 * hinaus</b> — wer mehr gearbeitet hat als angeboten war, soll das abrechnen oder die Zahl bewusst
 * herunterziehen; was zu viel ist, zeigt die Maske als Ueberschreitung. Eine buchbare Position ohne
 * Stunden in diesem Monat steht nicht im Entwurf, und eine Position, auf die sich keine Zeit buchen
 * laesst, bekommt wie immer ihre offene Menge (E1). Der Leistungszeitraum ist dann der gewaehlte
 * Monat.
 *
 * <p><b>Ein Monat ohne eine einzige Stunde ergibt den Entwurf ohne Monat</b> (E7), samt laufendem
 * Monat im Leistungszeitraum: Sonst entstuende ein Entwurf ohne jede Menge an den buchbaren
 * Positionen, und der Freiberufler muesste jede Zeile von Hand nachtragen — mit einer leeren
 * Vorbelegung ist ihm weniger geholfen als mit der alten.
 *
 * <p>Abgewiesen wird vor allem anderen: ein Angebot vor {@code BESTELLT} und ein Angebot, an dem
 * nichts offen ist. Beides sagt {@link AngebotNichtAbrechenbar} — <b>auch mit einem Monat, in dem
 * Stunden stehen</b> (E2): Was angeboten war, ist dann abgerechnet, und eine weitere Rechnung
 * entsteht erst, wenn das Angebot die Mehrarbeit ausweist. Der Server entscheidet, nicht die
 * Auswahlliste der Oberflaeche.
 *
 * <p><b>Am stornierten Angebot</b> (#330, Kriterium 7; Plan #332, E8) tritt in beiden Vorbelegungen
 * und in der Abweisung die abrechenbare Menge aus {@link Abrechenbarkeit} an die Stelle der
 * offenen: die bis zum Storno erfassten, noch nicht abgerechneten Stunden. Eine Festpreisposition
 * traegt dort nichts mehr bei. Die Stunden eines gewaehlten Monats bleiben dagegen ungedeckelt, wie
 * seit Issue #193 — ein Deckel staende an der Monatsvorbelegung allein.
 */
@Service
@Transactional
public class RechnungAnlegenUseCase {

  /** Der Monat als Text, so wie ihn Kriterium 10 zeigt: „September 2026". */
  private static final DateTimeFormatter MONAT =
      DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN);

  private final AngebotRepository angebote;
  private final RechnungRepository rechnungen;
  private final Arbeitszeitauskunft arbeitszeit;
  private final Clock clock;

  RechnungAnlegenUseCase(
      final AngebotRepository angebote,
      final RechnungRepository rechnungen,
      final Arbeitszeitauskunft arbeitszeit,
      final Clock clock) {
    this.angebote = angebote;
    this.rechnungen = rechnungen;
    this.arbeitszeit = arbeitszeit;
    this.clock = clock;
  }

  /**
   * Legt zu einem Angebot einen Rechnungsentwurf an und liefert ihn mit seiner Kennung.
   *
   * @param angebotId Kennung des Angebots, das abgerechnet wird
   * @param monat der Monat, dessen Arbeitszeit die Mengen vorbelegt, oder leer fuer die Vorbelegung
   *     aus den offenen Mengen
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotNichtAbrechenbar wenn das Angebot vor {@code BESTELLT} steht oder an keiner
   *     seiner Positionen noch etwas abrechenbar ist
   */
  public Rechnung anlegen(final long angebotId, final Optional<YearMonth> monat) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    if (!Abrechenbarkeit.STATUS.contains(angebot.status())) {
      throw new AngebotNichtAbrechenbar();
    }
    final Abrechnungsstand stand =
        Abrechnungsstand.fuer(angebot.positionen(), rechnungen.findByAngebot(angebotId));
    final Map<Long, BigDecimal> angefallen = arbeitszeit.angefallen(angebotId);
    if (!stand.etwasAbrechenbar(angebot, angefallen)) {
      throw new AngebotNichtAbrechenbar();
    }
    final LocalDate heute = LocalDate.now(clock.withZone(Geschaeftszone.ZONE));
    final @Nullable YearMonth gewaehlt = monat.orElse(null);
    if (gewaehlt != null) {
      final Map<Long, BigDecimal> stunden = gebuchteStunden(angebot, gewaehlt, stand);
      if (!stunden.isEmpty()) {
        return schreibe(
            angebotId, heute, gewaehlt, nachArbeitszeit(angebot, stand, stunden, angefallen));
      }
    }
    return schreibe(
        angebotId, heute, YearMonth.from(heute), nachOffenem(angebot, stand, angefallen));
  }

  /*
   * Die Stunden des Monats, aber nur an den buchbaren Positionen und nur, wo etwas steht. Eine
   * leere Abbildung heisst damit „in diesem Monat ist nichts abzurechnen" und loest die Rueckkehr
   * zur Vorbelegung ohne Monat aus (E7). Gefiltert wird hier und nicht in der Auskunft: Die Regel,
   * was buchbar ist, steht in Buchbarkeit und nur dort.
   *
   * Das Angebot gehoert zur Frage, seit die interne Arbeit jede ihrer Positionen Stunden tragen
   * laesst (Issue #229, E11). Eine eigene Sperre gegen sie steht hier nicht: Die interne Arbeit
   * kommt nie bis hierher, weil LAEUFT und ABGESCHLOSSEN nicht in Abrechenbarkeit.STATUS stehen und
   * die Pruefung darauf oben in anlegen(...) steht (Kriterium 7 von #207). Eine zweite Bedingung
   * waere eine Abschrift derselben Aussage.
   */
  private Map<Long, BigDecimal> gebuchteStunden(
      final Angebot angebot, final YearMonth monat, final Abrechnungsstand stand) {
    final Map<Long, BigDecimal> gemeldet = arbeitszeit.imMonat(angebot.requireId(), monat);
    final Map<Long, BigDecimal> gebucht = new LinkedHashMap<>();
    for (final Positionsstand positionsstand : stand.positionen()) {
      final long positionId = positionsstand.position().requireId();
      final BigDecimal stunden = gemeldet.getOrDefault(positionId, BigDecimal.ZERO);
      if (Buchbarkeit.buchbar(angebot, positionsstand.position()) && stunden.signum() > 0) {
        gebucht.put(positionId, stunden);
      }
    }
    return gebucht;
  }

  /*
   * Zwei Mengen in einer Liste: die gebuchten Stunden an den buchbaren Positionen und die
   * abrechenbare Menge an allen anderen — ohne Storno die offene. Eine Position ohne Menge faellt
   * heraus: Eine Zeile ueber 0 waere eine Rechnung ueber nichts.
   */
  private static List<Rechnungsposition> nachArbeitszeit(
      final Angebot angebot,
      final Abrechnungsstand stand,
      final Map<Long, BigDecimal> stunden,
      final Map<Long, BigDecimal> angefallen) {
    final List<Rechnungsposition> positionen = new ArrayList<>();
    for (final Positionsstand positionsstand : stand.positionen()) {
      final Angebotsposition position = positionsstand.position();
      final @Nullable BigDecimal gebucht = stunden.get(position.requireId());
      if (gebucht != null) {
        positionen.add(zeile(position, gebucht));
      } else if (!Buchbarkeit.buchbar(angebot, position)) {
        abrechenbareZeile(angebot, positionsstand, angefallen).ifPresent(positionen::add);
      }
    }
    return positionen;
  }

  private static List<Rechnungsposition> nachOffenem(
      final Angebot angebot, final Abrechnungsstand stand, final Map<Long, BigDecimal> angefallen) {
    return stand.positionen().stream()
        .flatMap(positionsstand -> abrechenbareZeile(angebot, positionsstand, angefallen).stream())
        .toList();
  }

  /* Die Zeile ueber die abrechenbare Menge der Position, oder keine, wo diese 0 ist. */
  private static Optional<Rechnungsposition> abrechenbareZeile(
      final Angebot angebot,
      final Positionsstand positionsstand,
      final Map<Long, BigDecimal> angefallen) {
    final Angebotsposition position = positionsstand.position();
    final BigDecimal menge =
        Abrechenbarkeit.abrechenbareMenge(
            angebot,
            positionsstand,
            angefallen.getOrDefault(position.requireId(), BigDecimal.ZERO));
    return menge.signum() > 0 ? Optional.of(zeile(position, menge)) : Optional.empty();
  }

  private static Rechnungsposition zeile(final Angebotsposition position, final BigDecimal menge) {
    return new Rechnungsposition(
        position.requireId(),
        position.bezeichnung(),
        menge,
        position.einheit(),
        position.einzelpreis());
  }

  private Rechnung schreibe(
      final long angebotId,
      final LocalDate heute,
      final YearMonth zeitraum,
      final List<Rechnungsposition> positionen) {
    final Instant jetzt = clock.instant();
    return rechnungen.save(
        new Rechnung(
            null,
            angebotId,
            Rechnungszustand.ENTWURF,
            heute,
            MONAT.format(zeitraum),
            positionen,
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
}
