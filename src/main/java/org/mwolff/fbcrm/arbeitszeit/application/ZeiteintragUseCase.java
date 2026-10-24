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
import org.mwolff.fbcrm.auth.application.GetCurrentAccountUseCase;
import org.mwolff.fbcrm.auth.application.MitarbeitendeVerwaltung;
import org.mwolff.fbcrm.auth.application.UnknownAccount;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die drei Schreibwege eines Zeiteintrags: anlegen, aendern, loeschen (Issue #193, Kriterien 1, 3,
 * 4 und 6; Plan #426, E6, E7, E12, E13, E19).
 *
 * <p><b>Die Reihenfolge der Pruefungen ist Absicht</b> (Plan #194, A19, A7, A8; Plan #426):
 *
 * <ol>
 *   <li>Ein Konto mit der Rolle ReadOnly wird vor allem anderen abgewiesen, gleich wem der Eintrag
 *       gehoert (E19) — auch auf eigenen Eintraegen aus der Zeit vor einem Rollenwechsel, die
 *       bestehen bleiben (#410, Kriterium 4). Beim Aendern und Loeschen ueber {@link
 *       ZeiteintragNichtGefunden}, beim Anlegen ueber {@link PersonNichtWaehlbar}.
 *   <li>Aendern und Loeschen lesen den Eintrag und weisen ihn als nicht gefunden ab, wenn er dem
 *       anfragenden Konto nicht gehoert und dieses nicht C-Level ist (E7): Eine 403 bestaetigte
 *       einem Employee, dass es die Kennung gibt.
 *   <li>Beim Aendern und Loeschen das Kennzeichen, dann der vorgelegte Stand (#411, Kriterien 1, 4
 *       und 6; Plan #415, E1, E14, E16, E17): Ist der Eintrag inzwischen geloescht, wird mit {@link
 *       ZeiteintragGeloescht} samt Urheber und Zeitpunkt der Loeschung abgewiesen; weicht der Stand
 *       ab, mit {@link ZeiteintragGeaendert}. Beides vor jeder fachlichen Pruefung und bevor etwas
 *       geschrieben wird — auch wenn ein C-Level einen fremden Eintrag korrigiert. Erst nach der
 *       Rechtepruefung: Einem Konto, das den Eintrag nicht sehen darf, verriete ein Konflikt
 *       Urheber und Zeitpunkt.
 *   <li>Die Person bestimmen und als waehlbar pruefen (E12). Beim Anlegen heisst eine fehlende
 *       Angabe „gehoert dem anfragenden Konto", beim Aendern „der Eigentuemer bleibt, der er ist" —
 *       ein Aendern ohne Angabe haengt einen fremden Eintrag nie um. Geprueft wird nur eine Person,
 *       die von der bisherigen abweicht; bleibt der Eintrag bei seiner Person, ist deren Zustand
 *       ohne Belang, sonst waere der Eintrag einer stillgelegten Person nicht mehr korrigierbar
 *       (#410, Kriterium 9). Eine andere Person nennen darf nur ein C-Level, und nur eine aktive.
 *   <li>Raster und {@code bis > von} — beides haengt allein an der Eingabe und braucht keinen Blick
 *       in den Bestand. Verstoesse kommen als Meldung am Feld {@code von} oder {@code bis} an,
 *       nicht als Fehler der Datenbank (A19).
 *   <li>„Buchung zulaessig" — nur beim Anlegen und beim <b>Wechsel</b> der Position (A7). Tag und
 *       Uhrzeit eines Eintrags an einem inzwischen abgerechneten Angebot bleiben damit aenderbar,
 *       und Loeschen ist immer erlaubt: Ein vergessener Tippfehler soll korrigierbar bleiben (Issue
 *       #193, Antwort 4).
 *   <li>Die Ueberschneidung — ueber <b>alle</b> Positionen desselben Tages <b>derselben Person</b>,
 *       beim Aendern ohne den Eintrag selbst (A8; Plan #426, E8). Beim Umhaengen zaehlen die Zeiten
 *       der neuen Person, und die Meldung nennt deren kollidierenden Eintrag (E13).
 * </ol>
 *
 * <p><b>Welche Eintraege verglichen werden, entscheidet dieser Anwendungsfall</b>, und ob zwei sich
 * ueberschneiden, sagt {@link Zeiteintrag#ueberschneidet}. Gefragt wird mit {@code
 * findImZeitraumFuerKonto} ueber denselben Tag in beiden Grenzen und das Konto, dem der Eintrag
 * gehoeren soll (E8) — fremde Eintraege kommen so gar nicht erst in den Speicher. Die Datenbank
 * prueft die Ueberschneidung bewusst nicht: Nur hier ist der kollidierende Eintrag bekannt, den die
 * Meldung nennen muss (A4).
 *
 * <p><b>Rolle und Zustand kommen frisch aus dem Konto</b> und nicht aus dem Token (E6), ueber die
 * Lesewege von {@code auth.application} — dieselbe Richtung wie in {@link Personenauskunft}. Der
 * Controller reicht allein die Kennung des angemeldeten Kontos weiter.
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
  private final GetCurrentAccountUseCase aktuellesKonto;
  private final MitarbeitendeVerwaltung konten;
  private final Clock clock;

  ZeiteintragUseCase(
      final ZeiteintragRepository zeiten,
      final AngebotRepository angebote,
      final FirmaRepository firmen,
      final GetCurrentAccountUseCase aktuellesKonto,
      final MitarbeitendeVerwaltung konten,
      final Clock clock) {
    this.zeiten = zeiten;
    this.angebote = angebote;
    this.firmen = firmen;
    this.aktuellesKonto = aktuellesKonto;
    this.konten = konten;
    this.clock = clock;
  }

  /**
   * Erfasst eine Arbeitszeit auf einer Angebotsposition (Kriterium 1).
   *
   * <p>Der Eintrag gehoert der genannten Person, ohne Angabe dem anfragenden Konto (Plan #426,
   * E12). Fuer eine andere Person erfasst nur ein C-Level, und nur fuer eine aktive.
   *
   * @param accountId Kennung des anfragenden Kontos
   * @param person Kennung der Person, der der Eintrag gehoeren soll, oder leer fuer das anfragende
   *     Konto
   * @param angebotPositionId Kennung der Position, auf die gebucht wird
   * @param tag der Tag, an dem gearbeitet wurde
   * @param von Beginn, auf einer Viertelstunde
   * @param bis Ende, auf einer Viertelstunde und nach {@code von}
   * @throws UnknownAccount wenn es das anfragende Konto nicht mehr gibt
   * @throws PersonNichtWaehlbar wenn das anfragende Konto ReadOnly ist oder fuer die genannte
   *     Person nicht erfassen darf
   * @throws UhrzeitNichtImRaster wenn Beginn oder Ende nicht auf einer Viertelstunde liegen
   * @throws EndeVorBeginn wenn das Ende nicht nach dem Beginn liegt
   * @throws PositionNichtBuchbar wenn es die Position nicht gibt oder auf sie nicht gebucht werden
   *     darf
   * @throws ZeitenUeberschneidenSich wenn am selben Tag schon ein Eintrag dieser Person in diesem
   *     Zeitraum liegt
   */
  public Zeiteintrag anlegen(
      final long accountId,
      final Optional<Long> person,
      final long angebotPositionId,
      final LocalDate tag,
      final LocalTime von,
      final LocalTime bis) {
    final Account anfragend = aktuellesKonto.byId(accountId);
    if (anfragend.role() == Role.READ_ONLY) {
      throw new PersonNichtWaehlbar();
    }
    final long eigentuemer = person(anfragend, person, anfragend.requireId());
    pruefeZeiten(von, bis);
    pruefeBuchungZulaessig(angebotPositionId);
    final Instant jetzt = clock.instant();
    return gespeichert(
        new Zeiteintrag(null, eigentuemer, angebotPositionId, tag, von, bis, jetzt, jetzt));
  }

  /**
   * Aendert einen Zeiteintrag (Kriterium 6) — Tag, Uhrzeit, Position und, fuer ein C-Level, die
   * Person.
   *
   * <p>„Buchung zulaessig" wird nur geprueft, wenn die Position wechselt (A7); der Eintrag selbst
   * zaehlt bei der Ueberschneidung nicht mit (A8). Ohne Angabe bleibt er bei der Person, der er
   * gehoert (Plan #426, E12); eine genannte andere Person haengt ihn um, und geprueft wird dann
   * gegen deren Zeiten (E13).
   *
   * @param accountId Kennung des anfragenden Kontos — zugleich der Urheber der Aenderung (Plan
   *     #415, E14)
   * @param id Kennung des Eintrags
   * @param version der vorgelegte Stand aus {@code If-Match} (Plan #415, E2)
   * @param person Kennung der Person, der der Eintrag kuenftig gehoeren soll, oder leer fuer die
   *     bisherige
   * @param angebotPositionId Kennung der Position, auf die gebucht wird
   * @param tag der Tag, an dem gearbeitet wurde
   * @param von Beginn, auf einer Viertelstunde
   * @param bis Ende, auf einer Viertelstunde und nach {@code von}
   * @throws UnknownAccount wenn es das anfragende Konto nicht mehr gibt
   * @throws ZeiteintragNichtGefunden wenn es den Eintrag nicht gibt, er dem anfragenden Konto nicht
   *     gehoert und dieses nicht C-Level ist, oder das anfragende Konto ReadOnly ist
   * @throws ZeiteintragGeloescht wenn der Eintrag inzwischen geloescht ist (Plan #415, E16)
   * @throws ZeiteintragGeaendert wenn der Eintrag inzwischen einen anderen Stand hat
   * @throws PersonNichtWaehlbar wenn die genannte Person von der bisherigen abweicht und das
   *     anfragende Konto den Eintrag nicht auf sie umhaengen darf
   * @throws UhrzeitNichtImRaster wenn Beginn oder Ende nicht auf einer Viertelstunde liegen
   * @throws EndeVorBeginn wenn das Ende nicht nach dem Beginn liegt
   * @throws PositionNichtBuchbar wenn die Position wechselt und auf die neue nicht gebucht werden
   *     darf
   * @throws ZeitenUeberschneidenSich wenn am selben Tag ein anderer Eintrag derselben Person in
   *     diesem Zeitraum liegt
   */
  public Zeiteintrag aendern(
      final long accountId,
      final long id,
      final long version,
      final Optional<Long> person,
      final long angebotPositionId,
      final LocalDate tag,
      final LocalTime von,
      final LocalTime bis) {
    final Account anfragend = aktuellesKonto.byId(accountId);
    final Zeiteintrag vorhandener = offener(anfragend, id, version);
    final long eigentuemer = person(anfragend, person, vorhandener.accountId());
    pruefeZeiten(von, bis);
    if (angebotPositionId != vorhandener.angebotPositionId()) {
      pruefeBuchungZulaessig(angebotPositionId);
    }
    return gespeichert(
        new Zeiteintrag(
                vorhandener.id(),
                eigentuemer,
                angebotPositionId,
                tag,
                von,
                bis,
                vorhandener.createdAt(),
                clock.instant(),
                vorhandener.version(),
                vorhandener.geaendertVon(),
                vorhandener.geloeschtAm(),
                vorhandener.geloeschtVon())
            .durch(accountId));
  }

  /**
   * Loescht den Zeiteintrag mit Kennzeichen (A5, Antwort 4; Plan #415, E17): Die Zeile bleibt mit
   * Zeitpunkt und Konto der Loeschung und faellt aus jedem Leseweg und jeder Summe.
   *
   * <p>Ohne Blick in das Angebot: Loeschen ist immer erlaubt, auch an einem schon abgerechneten —
   * fuer den Eigentuemer und fuer ein C-Level (Plan #426, E7). Es beruht auf dem vorgelegten Stand
   * (#411, Kriterium 6).
   *
   * @param accountId Kennung des anfragenden Kontos — zugleich der Urheber der Loeschung (E14)
   * @param id Kennung des Eintrags
   * @param version der vorgelegte Stand aus {@code If-Match} (Plan #415, E2)
   * @throws UnknownAccount wenn es das anfragende Konto nicht mehr gibt
   * @throws ZeiteintragNichtGefunden wenn es den Eintrag nicht gibt, er dem anfragenden Konto nicht
   *     gehoert und dieses nicht C-Level ist, oder das anfragende Konto ReadOnly ist
   * @throws ZeiteintragGeloescht wenn der Eintrag inzwischen geloescht ist (E16)
   * @throws ZeiteintragGeaendert wenn der Eintrag inzwischen einen anderen Stand hat
   */
  public void loeschen(final long accountId, final long id, final long version) {
    zeiten.save(
        offener(aktuellesKonto.byId(accountId), id, version).geloescht(accountId, clock.instant()));
  }

  /*
   * Der Eintrag, auf dem ein Aendern oder Loeschen beruhen darf: schreibbar, nicht geloescht und
   * auf dem vorgelegten Stand. ReadOnly zuerst und ohne Blick in den Bestand (E19); danach ist ein
   * fremder Eintrag fuer ein Konto, das nicht C-Level ist, einer, den es nicht gibt (E7). Erst dann
   * das Kennzeichen, und es geht dem Stand vor — wer einen geloeschten Eintrag bearbeitet, soll von
   * der Loeschung erfahren und nicht von einem Stand, den es nicht mehr gibt.
   */
  private Zeiteintrag offener(final Account anfragend, final long id, final long version) {
    if (anfragend.role() == Role.READ_ONLY) {
      throw new ZeiteintragNichtGefunden();
    }
    final long handelnd = anfragend.requireId();
    final Zeiteintrag vorhandener = zeiten.findById(id).orElseThrow(ZeiteintragNichtGefunden::new);
    if (vorhandener.accountId() != handelnd && anfragend.role() != Role.C_LEVEL) {
      throw new ZeiteintragNichtGefunden();
    }
    final Instant geloeschtAm = vorhandener.geloeschtAm();
    if (geloeschtAm != null) {
      throw new ZeiteintragGeloescht(vorhandener.geloeschtVon(), handelnd, geloeschtAm);
    }
    Bearbeitungskonflikt.pruefe(
        version,
        vorhandener.version(),
        () ->
            new ZeiteintragGeaendert(
                vorhandener.geaendertVon(), handelnd, vorhandener.updatedAt()));
    return vorhandener;
  }

  /*
   * Die Person, der der Eintrag gehoeren soll (E12). Ohne Angabe oder mit der bisherigen bleibt
   * es bei ihr, ohne Blick auf ihren Zustand (Kriterium 9). Beim Anlegen ist die bisherige das
   * anfragende Konto selbst — und das ist aktiv, sonst haette es keine Sitzung. Eine andere nennt
   * nur ein C-Level, und nur eine aktive; die Liste der Konten liest erst er, ein Employee kommt
   * nie bis dorthin.
   */
  private long person(final Account anfragend, final Optional<Long> genannt, final long bisherige) {
    final long gewuenscht = genannt.map(Long::longValue).orElse(bisherige);
    if (gewuenscht == bisherige) {
      return bisherige;
    }
    if (anfragend.role() != Role.C_LEVEL
        || konten.liste().stream()
            .noneMatch(konto -> konto.requireId() == gewuenscht && konto.aktiv())) {
      throw new PersonNichtWaehlbar();
    }
    return gewuenscht;
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

  /*
   * Welchen Satz die Abweisung traegt, entscheidet die Art des Angebots (E22): Der Satz des
   * Kundenangebots nennt Aufwand und Stunden, und an der internen Arbeit waere das falsch — dort
   * traegt jede Position Stunden, und es fehlt allein der Status.
   */
  private void pruefeBuchungZulaessig(final long angebotPositionId) {
    final Buchungsziel ziel = ziel(angebotPositionId);
    if (!Buchbarkeit.buchungZulaessig(ziel.angebot(), ziel.position())) {
      throw ziel.angebot().intern()
          ? PositionNichtBuchbar.anInternerArbeit()
          : PositionNichtBuchbar.amKundenangebot();
    }
  }

  /*
   * Erst pruefen, dann schreiben: Ein abgewiesener Eintrag hinterlaesst nichts. Der Kandidat traegt
   * beim Aendern schon seine Kennung — daran erkennt die Pruefung ihn selbst und zaehlt ihn nicht
   * mit; beim Anlegen ist sie null und trifft auf keine gespeicherte Kennung.
   */
  private Zeiteintrag gespeichert(final Zeiteintrag kandidat) {
    final Optional<Zeiteintrag> kollision =
        zeiten
            .findImZeitraumFuerKonto(kandidat.tag(), kandidat.tag(), kandidat.accountId())
            .stream()
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
    final String firmaName =
        firmen
            .findById(ziel.angebot().firmaId())
            .map(Firma::name)
            .orElseThrow(FirmaNichtGefunden::new);
    return new ZeitenUeberschneidenSich(
        anderer.von(), anderer.bis(), ziel.position().bezeichnung(), firmaName);
  }

  /*
   * Die Position samt ihrem Angebot. Der Bestand der Angebote kennt keinen Zugriff ueber die
   * Kennung einer Position — er liefert das Angebot als Ganzes (AngebotRepository) —, und genau
   * das Ganze ist hier gebraucht: Die Regel liest Abrechnungsart und Einheit der Position und
   * Kennzeichen und Status ihres Angebots. Eine unbekannte Kennung ist dieselbe Lage wie eine nicht buchbare
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
        .orElseThrow(PositionNichtBuchbar::amKundenangebot);
  }

  /** Eine Angebotsposition mit dem Angebot, zu dem sie gehoert. */
  private record Buchungsziel(Angebot angebot, Angebotsposition position) {}
}
