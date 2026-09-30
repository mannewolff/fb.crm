package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Aendern eines Rechnungsentwurfs (#160, Kriterien 4, 5, 9, 10, 12, 14).
 *
 * <p>Geschrieben wird der Entwurf als Ganzes: Datum, Leistungszeitraum und die vollstaendige Liste
 * der Angaben. Eine Angabe ueber die Menge 0 faellt weg — eine Position ueber nichts ist keine
 * Position (Kriterium 5). Mehr abzurechnen als offen ist bleibt erlaubt; das Angebot ist ein
 * Merkzettel und kein Vertrag (Frage 4).
 *
 * <p><b>Der Einzelpreis ist an der Rechnung nicht aenderbar</b> (Frage 5). Er bleibt der, den der
 * Entwurf schon traegt; eine spaetere Preisaenderung am Angebot erreicht ihn nicht (Frage 15, wozu
 * der Entwurf sonst unter der Hand ein anderer wuerde). Nur eine Position, die der Entwurf noch
 * nicht trug, nimmt den Preis des Angebots von jetzt — sie entsteht jetzt, und fuer sie gilt, was
 * jetzt gilt.
 *
 * <p><b>Der Zustand wird zuerst geprueft</b> und nicht erst von {@link Rechnung#geaendert}: Fuer
 * eine gestellte Rechnung soll weder das Angebot geladen noch eine Angabe geprueft werden, und die
 * Abweisung soll den Zustand nennen und nicht eine Position. Die Domaene haelt die Grenze trotzdem
 * — dort fuehrt kein Weg daran vorbei.
 */
@Service
@Transactional
public class RechnungAendernUseCase {

  private final RechnungRepository rechnungen;
  private final AngebotRepository angebote;
  private final Clock clock;

  RechnungAendernUseCase(
      final RechnungRepository rechnungen, final AngebotRepository angebote, final Clock clock) {
    this.rechnungen = rechnungen;
    this.angebote = angebote;
    this.clock = clock;
  }

  /**
   * Aendert den Entwurf und liefert ihn in seinem neuen Stand.
   *
   * @param rechnungId Kennung der Rechnung
   * @param daten die eingereichten Angaben samt vollstaendiger Positionsliste
   * @throws RechnungNichtGefunden wenn es die Rechnung nicht gibt
   * @throws RechnungszustandPasstNicht wenn die Rechnung schon gestellt ist
   * @throws AngebotNichtGefunden wenn es das Angebot der Rechnung nicht gibt
   * @throws AbrechnungsangabenNichtWaehlbar wenn eine Menge negativ ist oder eine Angabe sich auf
   *     eine Position beruft, die nicht zum Angebot dieser Rechnung gehoert
   */
  public Rechnung aendere(final long rechnungId, final RechnungDaten daten) {
    final Rechnung rechnung =
        rechnungen.findById(rechnungId).orElseThrow(RechnungNichtGefunden::new);
    if (rechnung.zustand() != Rechnungszustand.ENTWURF) {
      throw new RechnungszustandPasstNicht();
    }
    final Angebot angebot =
        angebote.findById(rechnung.angebotId()).orElseThrow(AngebotNichtGefunden::new);
    final Map<Long, Angebotsposition> ausAngebot =
        angebot.positionen().stream()
            .collect(Collectors.toMap(Angebotsposition::requireId, Function.identity()));
    final Map<Long, Rechnungsposition> ausEntwurf =
        rechnung.positionen().stream()
            .collect(Collectors.toMap(Rechnungsposition::angebotPositionId, Function.identity()));
    final List<Rechnungsposition> neue = new ArrayList<>();
    for (final Abrechnungsangabe angabe : daten.angaben()) {
      final Angebotsposition ausgangspunkt = ausAngebot.get(angabe.angebotPositionId());
      if (ausgangspunkt == null || angabe.menge().signum() < 0) {
        throw new AbrechnungsangabenNichtWaehlbar();
      }
      if (angabe.menge().signum() > 0) {
        neue.add(
            new Rechnungsposition(
                angabe.angebotPositionId(),
                angabe.bezeichnung(),
                angabe.menge(),
                ausgangspunkt.einheit(),
                preisFuer(ausEntwurf.get(angabe.angebotPositionId()), ausgangspunkt)));
      }
    }
    return rechnungen.save(
        rechnung.geaendert(
            daten.rechnungDatum(), daten.leistungszeitraum(), neue, clock.instant()));
  }

  /*
   * Der Preis des Entwurfs schlaegt den des Angebots: Was der Entwurf schon traegt, wurde beim
   * Anlegen festgehalten (Frage 15). Nur eine Position, die er noch nicht trug, nimmt den Preis von
   * jetzt.
   */
  private static BigDecimal preisFuer(
      final @Nullable Rechnungsposition vorhanden, final Angebotsposition ausgangspunkt) {
    return vorhanden == null ? ausgangspunkt.einzelpreis() : vorhanden.einzelpreis();
  }
}
