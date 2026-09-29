package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.BelegnummerRepository;
import org.mwolff.fbcrm.angebot.domain.DokumentSpeicher;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Versenden eines Angebots (Kriterien 10 bis 16).
 *
 * <p>„Versenden" macht aus dem Entwurf ein festes Dokument: Nummer, Kopien der Anschriften, PDF.
 * <b>Verschickt wird dabei nichts</b> (Kriterium 16) — die Mail schreibt der Freiberufler selbst;
 * „versendet" heisst: Diese Fassung hat der Kunde bekommen.
 *
 * <p><b>Die Reihenfolge ist die Zusage.</b> Erst wird geprueft — Zustand, dann die Vollstaendigkeit
 * aus {@link VersandVoraussetzungen} —, und nur danach wird die Nummer gezogen. Ein abgewiesener
 * Versuch verbraucht damit keine Angebotsnummer und legt kein Objekt ab (Kriterium 12, E22). Dass
 * ein Ruecklauf der Transaktion die Nummer ebenfalls zurueckgaebe, ist die zweite Sicherung und
 * nicht der Ersatz fuer die erste.
 *
 * <p><b>Das Jahr der Nummer ist das Kalenderjahr der Geschaeftszone</b> (Kriterium 11, E12). Der
 * {@code Clock}-Bean ist {@code systemUTC()}; ein Versand am 1. Januar um 00:30 deutscher Zeit
 * bekaeme sonst die Nummer des alten Jahres.
 *
 * <p><b>Alles in einer Transaktion.</b> Nummer, PDF und Festschreibung gehoeren zusammen; bleibt
 * eines davon aus, ist keines davon geschehen. Einzige Ausnahme ist der Objektspeicher: Ein
 * abgelegtes PDF, dessen Transaktion zurueckrollt, bleibt als verwaistes Objekt liegen — es traegt
 * einen eigenen Zufallsschluessel und wird von niemandem gefunden.
 *
 * <p>Was neben dem Angebot liegt — Firma, Ansprechpartner, „Eigene Angaben" — holt {@link
 * Versandunterlagen}, und die Kopien macht {@link Belegangaben}. Hier steht die Reihenfolge.
 */
@Service
@Transactional
public class AngebotVersendenUseCase {

  private final AngebotRepository angebote;
  private final Versandunterlagen unterlagen;
  private final BelegnummerRepository belegnummern;
  private final Belegdrucker drucker;
  private final DokumentSpeicher speicher;
  private final Clock clock;

  public AngebotVersendenUseCase(
      final AngebotRepository angebote,
      final Versandunterlagen unterlagen,
      final BelegnummerRepository belegnummern,
      final Belegdrucker drucker,
      final DokumentSpeicher speicher,
      final Clock clock) {
    this.angebote = angebote;
    this.unterlagen = unterlagen;
    this.belegnummern = belegnummern;
    this.drucker = drucker;
    this.speicher = speicher;
    this.clock = clock;
  }

  /**
   * Schreibt den Entwurf fest und liefert ihn in seinem neuen Stand.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotNichtAenderbar wenn das Angebot kein Entwurf mehr ist
   * @throws FirmaNichtGefunden wenn es die Firma des Angebots nicht gibt
   * @throws VersandUnvollstaendig wenn eine Angabe aus Kriterium 12 fehlt
   */
  public AngebotAnsicht versende(final long angebotId) {
    final Angebot entwurf = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    if (entwurf.zustand() != Angebotszustand.ENTWURF) {
      throw new AngebotNichtAenderbar();
    }
    final Belegangaben angaben = unterlagen.zu(entwurf);
    VersandVoraussetzungen.pruefe(entwurf, angaben.firma(), angaben.eigeneAngaben());
    return AngebotAnsicht.of(festgeschrieben(entwurf, angaben), clock);
  }

  /*
   * Ab hier wird geschrieben — und erst ab hier: Jede Pruefung liegt hinter uns, die Nummer ist also
   * keine, die ein abgewiesener Versuch verbraucht haette.
   */
  private Angebot festgeschrieben(final Angebot entwurf, final Belegangaben angaben) {
    final Instant jetzt = clock.instant();
    final LocalDate heute = AngebotAnsicht.heute(clock);
    final String nummer = belegnummern.zieheNummer(heute.getYear());
    final long angebotId = entwurf.requireId();
    final String schluessel =
        speicher.lege(
            angebotId, drucker.drucke(Beleglayout.zeilen(druckdaten(entwurf, nummer, angaben))));
    return angebote.save(
        entwurf.versendet(nummer, angaben.empfaenger(), angaben.absender(), schluessel, jetzt));
  }

  /*
   * Die Summe geht als Wert auf den Beleg und nicht als Verweis: Das abgelegte Dokument darf sich
   * nicht aendern, wenn sich spaeter eine Rechenregel aendert (Kriterium 14).
   */
  private static AngebotDruckdaten druckdaten(
      final Angebot entwurf, final String nummer, final Belegangaben angaben) {
    return new AngebotDruckdaten(
        angaben.absender(),
        angaben.empfaenger(),
        nummer,
        entwurf.angebotDatum(),
        entwurf.gueltigBis(),
        entwurf.leistungsbeschreibung(),
        entwurf.positionen(),
        entwurf.summe(),
        entwurf.zahlungsbedingungen());
  }
}
