package org.mwolff.fbcrm.arbeitszeit.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;
import org.mwolff.fbcrm.arbeitszeit.domain.ZeiteintragRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die drei Schreibwege eines Zeiteintrags: anlegen, aendern, loeschen (Issue #193, Kriterien 1, 3,
 * 4 und 6).
 *
 * <p><b>Die Reihenfolge der Pruefungen ist Absicht</b> (Plan #194, A19, A7, A8):
 *
 * <ol>
 *   <li>Raster und {@code bis > von} — beides haengt allein an der Eingabe und braucht keinen Blick
 *       in den Bestand. Verstoesse kommen als Meldung am Feld {@code von} oder {@code bis} an,
 *       nicht als Fehler der Datenbank (A19).
 *   <li>„Buchung zulaessig" — nur beim Anlegen und beim <b>Wechsel</b> der Position (A7). Tag und
 *       Uhrzeit eines Eintrags an einem inzwischen abgerechneten Angebot bleiben damit aenderbar,
 *       und Loeschen ist immer erlaubt: Ein vergessener Tippfehler soll korrigierbar bleiben (Issue
 *       #193, Antwort 4).
 *   <li>Die Ueberschneidung — ueber <b>alle</b> Positionen desselben Tages, beim Aendern ohne den
 *       Eintrag selbst (A8).
 * </ol>
 *
 * <p><b>Welche Eintraege verglichen werden, entscheidet dieser Anwendungsfall</b>, und ob zwei sich
 * ueberschneiden, sagt {@link Zeiteintrag#ueberschneidet}. Gefragt wird mit {@code findImZeitraum}
 * ueber denselben Tag in beiden Grenzen; eine eigene Abfrage je Tag waere dieselbe unter zweitem
 * Namen. Die Datenbank prueft die Ueberschneidung bewusst nicht: Nur hier ist der kollidierende
 * Eintrag bekannt, den die Meldung nennen muss (A4).
 *
 * <p>Der Zeitpunkt kommt aus der injizierten {@link Clock} (CLAUDE-java.md §6.2). Beim Aendern
 * bleibt {@code createdAt} stehen.
 */
@Service
@Transactional
public class ZeiteintragUseCase {

  private final ZeiteintragRepository zeiten;
  private final AngebotRepository angebote;
  private final FirmaRepository firmen;
  private final Clock clock;

  ZeiteintragUseCase(
      final ZeiteintragRepository zeiten,
      final AngebotRepository angebote,
      final FirmaRepository firmen,
      final Clock clock) {
    this.zeiten = zeiten;
    this.angebote = angebote;
    this.firmen = firmen;
    this.clock = clock;
  }

  /**
   * Erfasst eine Arbeitszeit auf einer Angebotsposition (Kriterium 1).
   *
   * @param angebotPositionId Kennung der Position, auf die gebucht wird
   * @param tag der Tag, an dem gearbeitet wurde
   * @param von Beginn, auf einer Viertelstunde
   * @param bis Ende, auf einer Viertelstunde und nach {@code von}
   * @throws UhrzeitNichtImRaster wenn Beginn oder Ende nicht auf einer Viertelstunde liegen
   * @throws EndeVorBeginn wenn das Ende nicht nach dem Beginn liegt
   * @throws PositionNichtBuchbar wenn es die Position nicht gibt oder auf sie nicht gebucht werden
   *     darf
   * @throws ZeitenUeberschneidenSich wenn am selben Tag schon ein Eintrag in diesem Zeitraum liegt
   */
  public Zeiteintrag anlegen(
      final long angebotPositionId, final LocalDate tag, final LocalTime von, final LocalTime bis) {
    pruefeZeiten(von, bis);
    pruefeBuchungZulaessig(angebotPositionId);
    final Instant jetzt = clock.instant();
    return gespeichert(new Zeiteintrag(null, angebotPositionId, tag, von, bis, jetzt, jetzt));
  }

  /**
   * Aendert einen Zeiteintrag (Kriterium 6) — Tag, Uhrzeit und Position.
   *
   * <p>„Buchung zulaessig" wird nur geprueft, wenn die Position wechselt (A7); der Eintrag selbst
   * zaehlt bei der Ueberschneidung nicht mit (A8).
   *
   * @param id Kennung des Eintrags
   * @param angebotPositionId Kennung der Position, auf die gebucht wird
   * @param tag der Tag, an dem gearbeitet wurde
   * @param von Beginn, auf einer Viertelstunde
   * @param bis Ende, auf einer Viertelstunde und nach {@code von}
   * @throws ZeiteintragNichtGefunden wenn es den Eintrag nicht gibt
   * @throws UhrzeitNichtImRaster wenn Beginn oder Ende nicht auf einer Viertelstunde liegen
   * @throws EndeVorBeginn wenn das Ende nicht nach dem Beginn liegt
   * @throws PositionNichtBuchbar wenn die Position wechselt und auf die neue nicht gebucht werden
   *     darf
   * @throws ZeitenUeberschneidenSich wenn am selben Tag ein anderer Eintrag in diesem Zeitraum
   *     liegt
   */
  public Zeiteintrag aendern(
      final long id,
      final long angebotPositionId,
      final LocalDate tag,
      final LocalTime von,
      final LocalTime bis) {
    final Zeiteintrag vorhandener = lies(id);
    pruefeZeiten(von, bis);
    if (angebotPositionId != vorhandener.angebotPositionId()) {
      pruefeBuchungZulaessig(angebotPositionId);
    }
    return gespeichert(
        new Zeiteintrag(
            vorhandener.id(),
            angebotPositionId,
            tag,
            von,
            bis,
            vorhandener.createdAt(),
            clock.instant()));
  }

  /**
   * Loescht den Zeiteintrag — die Zeile ist danach fort (A5, Antwort 4).
   *
   * <p>Ohne Blick in das Angebot: Loeschen ist immer erlaubt, auch an einem schon abgerechneten.
   *
   * @param id Kennung des Eintrags
   * @throws ZeiteintragNichtGefunden wenn es den Eintrag nicht gibt
   */
  public void loeschen(final long id) {
    zeiten.delete(lies(id).requireId());
  }

  private Zeiteintrag lies(final long id) {
    return zeiten.findById(id).orElseThrow(ZeiteintragNichtGefunden::new);
  }

  private static void pruefeZeiten(final LocalTime von, final LocalTime bis) {
    if (!Zeiteintrag.imRaster(von)) {
      throw UhrzeitNichtImRaster.anVon();
    }
    if (!Zeiteintrag.imRaster(bis)) {
      throw UhrzeitNichtImRaster.anBis();
    }
    if (!bis.isAfter(von)) {
      throw new EndeVorBeginn();
    }
  }

  private void pruefeBuchungZulaessig(final long angebotPositionId) {
    final Buchungsziel ziel = ziel(angebotPositionId);
    if (!Buchbarkeit.buchungZulaessig(ziel.angebot(), ziel.position())) {
      throw new PositionNichtBuchbar();
    }
  }

  /*
   * Erst pruefen, dann schreiben: Ein abgewiesener Eintrag hinterlaesst nichts. Der Kandidat traegt
   * beim Aendern schon seine Kennung — daran erkennt die Pruefung ihn selbst und zaehlt ihn nicht
   * mit; beim Anlegen ist sie null und trifft auf keine gespeicherte Kennung.
   */
  private Zeiteintrag gespeichert(final Zeiteintrag kandidat) {
    final Optional<Zeiteintrag> kollision =
        zeiten.findImZeitraum(kandidat.tag(), kandidat.tag()).stream()
            .filter(anderer -> !Objects.equals(anderer.id(), kandidat.id()))
            .filter(kandidat::ueberschneidet)
            .findFirst();
    if (kollision.isPresent()) {
      throw ueberschneidung(kollision.get());
    }
    return zeiten.save(kandidat);
  }

  private ZeitenUeberschneidenSich ueberschneidung(final Zeiteintrag anderer) {
    final Buchungsziel ziel = ziel(anderer.angebotPositionId());
    return new ZeitenUeberschneidenSich(
        anderer.von(), anderer.bis(), ziel.position().bezeichnung(), firmaName(ziel.angebot()));
  }

  private String firmaName(final Angebot angebot) {
    return firmen.findById(angebot.firmaId()).map(Firma::name).orElseThrow(FirmaNichtGefunden::new);
  }

  /*
   * Die Position samt ihrem Angebot. Der Bestand der Angebote kennt keinen Zugriff ueber die
   * Kennung einer Position — er liefert das Angebot als Ganzes (AngebotRepository) —, und genau
   * das Ganze ist hier gebraucht: Die Regel liest Abrechnungsart und Einheit der Position und den
   * Status ihres Angebots. Eine unbekannte Kennung ist dieselbe Lage wie eine nicht buchbare
   * Position (siehe PositionNichtBuchbar). Auf dem Weg der Ueberschneidung kann sie nicht
   * auftreten: Der Fremdschluessel der Tabelle haelt jede gebuchte Position fest.
   */
  private Buchungsziel ziel(final long angebotPositionId) {
    return angebote.findAlle(Optional.empty()).stream()
        .flatMap(
            angebot ->
                angebot.positionen().stream()
                    .filter(position -> position.requireId() == angebotPositionId)
                    .map(position -> new Buchungsziel(angebot, position)))
        .findFirst()
        .orElseThrow(PositionNichtBuchbar::new);
  }

  /** Eine Angebotsposition mit dem Angebot, zu dem sie gehoert. */
  private record Buchungsziel(Angebot angebot, Angebotsposition position) {}
}
