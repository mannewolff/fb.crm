package org.mwolff.fbcrm.angebot.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Aendern eines Angebots — in jedem Status (Issue #127, Kriterium 5).
 *
 * <p>Geschrieben wird das Angebot als Ganzes: Datum, Ansprechpartner, Beschreibung, die Art und die
 * vollstaendige Positionsliste in der gewuenschten Reihenfolge (E8). Die Plaetze der Positionen
 * vergibt der Bestand daraus lueckenlos neu (E24). Die Firma bleibt, wie sie ist; der Status folgt
 * der Art.
 *
 * <p>Der Ansprechpartner geht durch {@link Ansprechpartnerwahl}: Ein neu gewaehlter muss zur Firma
 * gehoeren und aktiv sein, der bisherige bleibt wählbar.
 *
 * <p><b>Die Positionskennungen werden hier geprueft</b> und nur hier (Plan #169, E2). Eine Position
 * mit Kennung sagt „dieselbe Position wie vorher"; gueltig sind darum ausschliesslich die Kennungen
 * der Positionen des geladenen Angebots, und jede hoechstens einmal. Alles andere ist {@link
 * PositionenNichtWaehlbar} — geprueft vor dem Schreiben, damit eine abgewiesene Aenderung nichts
 * hinterlaesst. Der Bestand darf sich danach darauf verlassen: Dort ist eine fremde Kennung nur
 * noch ein Programmierfehler.
 *
 * <p><b>Berechnete Positionen sind gebunden</b> (#160, Kriterium 28). Eine Position, die in einer
 * Rechnung steht, darf nicht entfallen und weder ihre Einheit noch ihre Abrechnungsart wechseln —
 * sonst verloere die Rechnung ihren Bezug. Text, Menge, Preis und die Reihenfolge bleiben frei: Was
 * die Rechnung davon braucht, hat sie beim Anlegen festgehalten (Kriterium 9). Welche Positionen
 * das sind, sagt {@link Positionsverwendung}; das Angebot erfaehrt es ueber den Port und kennt das
 * Modul {@code rechnung} nicht (Plan #169, E1, E12). Geprueft wird wie die Kennungen vor dem
 * Schreiben.
 *
 * <p><b>Die Art des Angebots wechselt hier</b> (Issue #227, Kriterium 8 von #207), und zwar nur,
 * solange aus dem Angebot keine Rechnung entstanden ist — das sagt {@link Rechnungsbindung}, sonst
 * ist es {@link KennzeichenNichtAenderbar}. Mit der Art wandert der Status in deren Reihe ({@code
 * Angebot.umgestellt(...)}).
 *
 * <p><b>Die Pflicht der vier Angaben haengt an der Zielart</b> (E7, E8). Ein Angebot an einen
 * Kunden braucht je Position Menge, Einheit, Preis und Abrechnungsart — fehlt eine, ist das {@link
 * Positionsangaben}. Die interne Arbeit braucht keine davon: Eine vorhandene Position behaelt ihre
 * gespeicherten Werte, eine neue bekommt {@code AUFWAND}, {@code STUNDE} und zweimal 0. So bleiben
 * die vier Spalten pflichtig, und ein Wechsel nach innen und zurueck verliert keine Zahl.
 *
 * <p><b>Eine bebuchte Position zwingt beim Wechsel nach aussen zu Stunden</b> (Issue #228). Wird
 * ein internes Angebot eines an einen Kunden, muss eine Position mit erfasster Arbeitszeit nach
 * {@code AUFWAND} in {@code STUNDE} abrechnen — sonst stuende die Zeit an einer Position, die sie
 * nach den Regeln der Zeiterfassung nicht tragen darf, und der Abrechnungsstand zeigte sie mit 0,00
 * Stunden. Welche Positionen Zeit tragen, sagt {@link Zeitbindung}; das Angebot erfaehrt es ueber
 * den Port und kennt das Modul {@code arbeitszeit} nicht (Plan #218, E6). Geprueft wird allein der
 * Wechsel <b>intern → extern</b> (E19): Der Weg nach innen nimmt keiner Position ihre Stunden, und
 * das Entfernen einer bebuchten Position scheitert weiter am Fremdschluessel.
 *
 * <p><b>Die Reihenfolge der Pruefungen</b> ist Absicht: erst das Angebot, dann der Ansprechpartner,
 * die Positionskennungen, die Rechnungsbindung beim Artwechsel, die Bindung der berechneten
 * Positionen, die Angaben nach Zielart und zuletzt die erfasste Arbeitszeit. Die Sperre aus
 * Kriterium 8 ist die gruendlichere Aussage — wer bei bestehender Rechnung umstellen will, soll das
 * erfahren und nicht zuerst vier Feldfehler zu Positionen bekommen, die er gar nicht aendern
 * wollte.
 */
/*
 * PMD.TooManyMethods: Fuenf Pruefungen und vier Uebersetzer einer Positionsangabe ergeben zusammen
 * mit dem einen oeffentlichen Weg mehr als die zehn Methoden der Schwelle. Jede Pruefung traegt
 * hier ihren Namen und ihre Begruendung — sie zusammenzuziehen hiesse, die Reihenfolge der
 * Abweisungen in einer langen Methode zu verstecken, und sie auf mehrere Klassen zu verteilen
 * hiesse, denselben geladenen Stand und dieselbe Einreichung durch mehrere Haende zu geben. Beides
 * waere schlechter zu lesen als die Folge kleiner, benannter Schritte an einer Stelle — dasselbe
 * Vorgehen wie bei {@code Rechnungslayout}.
 */
@SuppressWarnings("PMD.TooManyMethods")
@Service
@Transactional
public class AngebotAendernUseCase {

  /** Menge und Einzelpreis einer neuen Position der internen Arbeit (E8). */
  private static final BigDecimal OHNE_ZAHL = BigDecimal.ZERO;

  private final AngebotRepository angebote;
  private final Ansprechpartnerwahl wahl;
  private final Positionsverwendung verwendung;
  private final Rechnungsbindung bindung;
  private final Zeitbindung zeit;
  private final Clock clock;

  AngebotAendernUseCase(
      final AngebotRepository angebote,
      final Ansprechpartnerwahl wahl,
      final Positionsverwendung verwendung,
      final Rechnungsbindung bindung,
      final Zeitbindung zeit,
      final Clock clock) {
    this.angebote = angebote;
    this.wahl = wahl;
    this.verwendung = verwendung;
    this.bindung = bindung;
    this.zeit = zeit;
    this.clock = clock;
  }

  /**
   * Aendert das Angebot und liefert es in seinem neuen Stand.
   *
   * @param angebotId Kennung des Angebots
   * @param daten die eingereichten Angaben samt vollstaendiger Positionsliste
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AnsprechpartnerNichtWaehlbar wenn der gewaehlte Ansprechpartner nicht zur Wahl steht
   * @throws PositionenNichtWaehlbar wenn eine Positionskennung nicht zu diesem Angebot gehoert oder
   *     zweimal eingereicht wurde
   * @throws PositionInRechnungVerwendet wenn eine Position, die in einer Rechnung steht, fehlt oder
   *     ihre Einheit oder ihre Abrechnungsart wechselt
   * @throws KennzeichenNichtAenderbar wenn die Art wechseln soll, obwohl eine Rechnung besteht
   * @throws Positionsangaben wenn einer Position Angaben fehlen, die ihre Zielart verlangt, oder
   *     wenn eine Position mit erfasster Arbeitszeit beim Wechsel nach aussen nicht nach Aufwand in
   *     Stunden abrechnet
   */
  public Angebot aendere(final long angebotId, final AngebotDaten daten) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    wahl.pruefe(angebot.firmaId(), daten.ansprechpartnerId(), angebot.ansprechpartnerId());
    pruefeKennungen(daten.positionen(), angebot.positionen());
    pruefeArt(angebotId, daten.intern(), angebot.intern());
    pruefeBindung(
        verwendung.verwendeteKennungen(angebotId), daten.positionen(), angebot.positionen());
    final List<Angebotsposition> positionen =
        nachZielart(daten.positionen(), angebot.positionen(), daten.intern());
    pruefeZeit(daten, angebot.intern());
    final Instant jetzt = clock.instant();
    return angebote.save(
        angebot
            .umgestellt(daten.intern(), jetzt)
            .geaendert(
                daten.angebotDatum(),
                daten.ansprechpartnerId(),
                daten.beschreibung(),
                positionen,
                jetzt));
  }

  /*
   * Die eingereichten Kennungen gegen die des geladenen Angebots. Geprueft wird die ganze Liste,
   * bevor irgendetwas geschrieben wird; eine Position ohne Kennung ist neu und geht durch.
   */
  private static void pruefeKennungen(
      final List<Positionsangabe> eingereicht, final List<Angebotsposition> vorhanden) {
    final Set<Long> offen =
        vorhanden.stream().map(Angebotsposition::id).collect(Collectors.toCollection(HashSet::new));
    for (final Positionsangabe angabe : eingereicht) {
      final Long kennung = angabe.id();
      // remove statt contains: Eine zweimal eingereichte Kennung faellt beim zweiten Mal durch.
      if (kennung != null && !offen.remove(kennung)) {
        throw new PositionenNichtWaehlbar();
      }
    }
  }

  /*
   * Die Art wechselt nur, solange keine Rechnung besteht (Kriterium 8). Gefragt wird allein beim
   * Wechsel: Wer nur den Text aendert, soll die Rechnungen seines Angebots nicht lesen lassen.
   */
  private void pruefeArt(final long angebotId, final boolean ziel, final boolean bisher) {
    if (ziel != bisher && bindung.rechnungVorhanden(angebotId)) {
      throw new KennzeichenNichtAenderbar();
    }
  }

  /*
   * Eine Position mit erfasster Arbeitszeit muss nach Aufwand in Stunden abrechnen, sobald das
   * Angebot eines an einen Kunden wird (Kriterium 8, E19). Gefragt wird allein bei diesem einen
   * Wechsel: Der Weg nach innen nimmt keiner Position ihre Stunden, und wer nichts umstellt, soll
   * die erfassten Zeiten seiner Positionen gar nicht erst lesen lassen. Eine Position ohne Kennung
   * ist neu und kann darum keine Zeit tragen.
   */
  private void pruefeZeit(final AngebotDaten daten, final boolean bisherIntern) {
    if (daten.intern() || !bisherIntern) {
      return;
    }
    // Erst die Positionen mit Kennung aussondern: Nur die koennen Zeit tragen, und danach ist die
    // Kennung ueberall ein long — eine unveraenderliche Menge weist contains(null) ohnehin mit
    // einer NullPointerException ab.
    final List<Positionsangabe> gespeicherte =
        daten.positionen().stream().filter(angabe -> angabe.id() != null).toList();
    final Set<Long> bebucht =
        zeit.mitZeit(
            gespeicherte.stream()
                .map(Positionsangabe::requireId)
                .collect(Collectors.toUnmodifiableSet()));
    for (final Positionsangabe angabe : gespeicherte) {
      if (bebucht.contains(angabe.requireId()) && !nachAufwandInStunden(angabe)) {
        throw Positionsangaben.zeitBrauchtAufwandInStunden(angabe.bezeichnung());
      }
    }
  }

  /*
   * Die erfasste Zeit sind Stunden: AUFWAND allein genuegt nicht, und ein Festpreis kennt sie gar
   * nicht. Gelesen wird aus der Einreichung, und die ist hier vollstaendig — nachZielart hat die
   * vier Angaben schon verlangt, weil die Zielart ein Angebot an einen Kunden ist.
   */
  private static boolean nachAufwandInStunden(final Positionsangabe angabe) {
    return angabe.abrechnungsmodus() == Abrechnungsmodus.AUFWAND
        && angabe.einheit() == Einheit.STUNDE;
  }

  /*
   * Die gebundenen Positionen gegen die eingereichte Liste. Gelesen wird vom gespeicherten Angebot
   * aus und nicht von der Einreichung: Eine gebundene Position, die dort fehlt, faellt nur so auf.
   * Die Bezeichnung der Meldung kommt aus demselben Grund vom gespeicherten Stand.
   */
  private static void pruefeBindung(
      final Set<Long> verwendet,
      final List<Positionsangabe> eingereicht,
      final List<Angebotsposition> vorhanden) {
    final Map<Long, Positionsangabe> jetzt = new HashMap<>();
    for (final Positionsangabe angabe : eingereicht) {
      // Eine Angabe ohne Kennung ist neu und kann darum keine gebundene Position fortschreiben.
      // requireId() statt der schon gelesenen Kennung: Der Schluessel ist hier ein long, und so
      // sagt die Zeile selbst, dass sie nur fuer gespeicherte Positionen gilt. Ohne das waere die
      // Pruefung wirkungslos — eine HashMap nimmt null als Schluessel an, und gelesen wird die
      // Abbildung allein mit der Kennung einer gespeicherten Position.
      if (angabe.id() != null) {
        jetzt.put(angabe.requireId(), angabe);
      }
    }
    for (final Angebotsposition gebunden : vorhanden) {
      final long kennung = gebunden.requireId();
      if (verwendet.contains(kennung) && !unveraendert(jetzt.get(kennung), gebunden)) {
        throw new PositionInRechnungVerwendet(gebunden.bezeichnung());
      }
    }
  }

  /*
   * Ob die gebundene Position die Einreichung unbeschadet uebersteht: Sie muss ueberhaupt dabei
   * sein, und Einheit wie Abrechnungsart muessen dieselben bleiben. Alles Uebrige darf sich aendern.
   * Eine Einreichung, die beide weglaesst, gilt dabei als Wechsel — ein gebundenes Angebot ist
   * immer eines an einen Kunden (nur dort entsteht eine Rechnung), und dort sind beide Pflicht.
   */
  private static boolean unveraendert(
      final @Nullable Positionsangabe eingereicht, final Angebotsposition gebunden) {
    return eingereicht != null
        && eingereicht.einheit() == gebunden.einheit()
        && eingereicht.abrechnungsmodus() == gebunden.abrechnungsmodus();
  }

  /*
   * Aus jeder Angabe eine vollstaendige Position — nach der Zielart und nach dem Zusammenfuehren mit
   * der gespeicherten Position (E7, E8). Der Platz in der Liste geht in den Feldnamen der Meldung
   * ein, darum die Zaehlung und nicht ein Stream.
   */
  private static List<Angebotsposition> nachZielart(
      final List<Positionsangabe> eingereicht,
      final List<Angebotsposition> vorhanden,
      final boolean intern) {
    final Map<Long, Angebotsposition> gespeichert = new HashMap<>();
    for (final Angebotsposition position : vorhanden) {
      gespeichert.put(position.id(), position);
    }
    final List<Angebotsposition> positionen = new ArrayList<>(eingereicht.size());
    for (int platz = 0; platz < eingereicht.size(); platz++) {
      positionen.add(position(eingereicht.get(platz), gespeichert, intern, platz));
    }
    return positionen;
  }

  private static Angebotsposition position(
      final Positionsangabe angabe,
      final Map<Long, Angebotsposition> gespeichert,
      final boolean intern,
      final int platz) {
    if (!intern) {
      return fuerKunden(angabe, platz);
    }
    final Angebotsposition bisher = angabe.id() == null ? null : gespeichert.get(angabe.id());
    return bisher == null ? neueInterne(angabe) : uebernommen(angabe, bisher);
  }

  /*
   * Ein Angebot an einen Kunden braucht alle vier Angaben. Die Ausnahme nennt jede fehlende auf
   * einmal — welche das sind, liest sie selbst aus der Angabe ab.
   */
  private static Angebotsposition fuerKunden(final Positionsangabe angabe, final int platz) {
    final Abrechnungsmodus modus = angabe.abrechnungsmodus();
    final BigDecimal menge = angabe.menge();
    final Einheit einheit = angabe.einheit();
    final BigDecimal einzelpreis = angabe.einzelpreis();
    if (modus == null || menge == null || einheit == null || einzelpreis == null) {
      throw Positionsangaben.fehlendeAngaben(platz, angabe);
    }
    return new Angebotsposition(
        angabe.id(), angabe.bezeichnung(), modus, menge, einheit, einzelpreis);
  }

  /*
   * Eine neue Position der internen Arbeit: AUFWAND, STUNDE und zweimal 0 (E8). Werte, die nichts
   * behaupten — die Maske zeigt sie bei interner Arbeit nicht, und die vier Spalten bleiben
   * pflichtig.
   */
  private static Angebotsposition neueInterne(final Positionsangabe angabe) {
    return new Angebotsposition(
        null, angabe.bezeichnung(), Abrechnungsmodus.AUFWAND, OHNE_ZAHL, Einheit.STUNDE, OHNE_ZAHL);
  }

  /*
   * Eine vorhandene Position der internen Arbeit behaelt ihre gespeicherten vier Werte (Kriterium
   * 8): Nur so findet ein Angebot, das nach innen und zurueck gestellt wird, seine Zahlen wieder.
   * Aenderbar bleibt allein die Bezeichnung — mehr zeigt die Maske dort nicht.
   */
  private static Angebotsposition uebernommen(
      final Positionsangabe angabe, final Angebotsposition bisher) {
    return new Angebotsposition(
        bisher.id(),
        angabe.bezeichnung(),
        bisher.abrechnungsmodus(),
        bisher.menge(),
        bisher.einheit(),
        bisher.einzelpreis());
  }
}
