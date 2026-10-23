package org.mwolff.fbcrm.arbeitszeit.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Eine erfasste Arbeitszeit: ein Tag, eine Uhrzeit von und bis, eine Angebotsposition (Issue #193,
 * Kriterium 1) — und die Person, der sie gehoert (Plan #426, E1).
 *
 * <p><b>Die Person steht als blosse Kennung des Kontos</b>, nicht als Name: Der Name kommt beim
 * Lesen aus dem Konto, damit nach einer Umbenennung alle Eintraege den aktuellen zeigen (#410,
 * Kriterium 9).
 *
 * <p><b>Die Dauer wird gerechnet, nicht gefuehrt</b> (Plan #194, A3). Eine gespeicherte Dauer waere
 * ein zweiter Wahrheitsort neben von und bis, der bei jeder Korrektur nachgezogen werden muesste —
 * dieselbe Ueberlegung wie bei {@code rechnung.domain.Positionsstand} und der offenen Menge.
 *
 * <p><b>Raster und {@code bis > von} sind Invarianten</b> (A19): Der kompakte Konstruktor weist
 * beides mit einer {@code IllegalArgumentException} ab. Das ist bewusst keine Meldung am Feld — die
 * liefert die Anwendungsschicht, die als einzige die Feldnamen der Schnittstelle kennt. Hier steht
 * die Pruefung, damit ein Eintrag, der durch einen anderen Weg als den Anwendungsfall entsteht,
 * nicht stillschweigend das Raster verlaesst und die CHECKs der Tabelle erst beim Schreiben
 * zuschlagen.
 *
 * <p><b>Die Obergrenze 23:45 folgt aus den beiden Invarianten</b> und steht nicht als eigene
 * Pruefung da (E6): {@link LocalTime} kennt kein 24:00, und 00:00 als Ende waere nie nach einem
 * Beginn. Die letzte Viertelstunde vor Mitternacht ist damit nicht erfassbar; das wird bewusst
 * hingenommen.
 *
 * <p><b>Eigene Version, eigener Urheber</b> (Plan #415, E1, E14): Ein Zeiteintrag ist ein Datensatz
 * fuer sich, und ein Aendern beruht auf seinem Stand — auch wenn ein C-Level einen fremden Eintrag
 * korrigiert (#410). Den Zaehler fuehrt der Bestand; {@link #durch(long)} haelt fest, wer zuletzt
 * geaendert hat.
 *
 * <p><b>Geloescht wird mit Kennzeichen</b> (Plan #415, E16, E17): {@link #geloescht(long, Instant)}
 * haelt Zeitpunkt und Konto der Loeschung fest, damit ein spaeterer Zugriff beides nennen kann. Aus
 * jedem Leseweg und jeder Summe faellt ein gekennzeichneter Eintrag heraus; das filtert der
 * Bestand.
 *
 * @param id technische Kennung — {@code null}, solange der Eintrag nicht gespeichert ist
 * @param accountId Kennung des Kontos, dem der Eintrag gehoert
 * @param angebotPositionId Kennung der Angebotsposition, auf die gebucht wurde
 * @param tag der Tag, an dem gearbeitet wurde
 * @param von Beginn, auf einer Viertelstunde ohne Sekunden
 * @param bis Ende, auf einer Viertelstunde ohne Sekunden; stets nach {@code von}
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 * @param version der Stand des Eintrags, wie ihn {@code If-Match} vorlegt (Plan #415, E1); 0 vor
 *     dem ersten Schreiben, danach fuehrt ihn der Bestand
 * @param geaendertVon Kennung des Kontos, das den Eintrag zuletzt geaendert hat, oder {@code null},
 *     solange er nie geaendert wurde
 * @param geloeschtAm Zeitpunkt der Loeschung, oder {@code null} fuer einen bestehenden Eintrag
 *     (E17)
 * @param geloeschtVon Kennung des Kontos, das den Eintrag geloescht hat, oder {@code null}
 */
public record Zeiteintrag(
    @Nullable Long id,
    long accountId,
    long angebotPositionId,
    LocalDate tag,
    LocalTime von,
    LocalTime bis,
    Instant createdAt,
    Instant updatedAt,
    long version,
    @Nullable Long geaendertVon,
    @Nullable Instant geloeschtAm,
    @Nullable Long geloeschtVon)
    implements Identifiable {

  /** Die Schrittweite der Erfassung in Minuten (Issue #193, Antwort 6). */
  private static final int VIERTELSTUNDE = 15;

  /** Der Teiler von Minuten zu Stunden, als {@link BigDecimal} fuer die exakte Division. */
  private static final BigDecimal MINUTEN_JE_STUNDE = BigDecimal.valueOf(60L);

  /** Die Zahl der Nachkommastellen einer Stundenangabe (E5). */
  private static final int NACHKOMMASTELLEN = 2;

  /** Prueft die beiden Invarianten; die Meldung am Feld kommt aus der Anwendungsschicht (A19). */
  public Zeiteintrag {
    if (!imRaster(von) || !imRaster(bis)) {
      throw new IllegalArgumentException(
          "Arbeitszeit wird in Schritten einer Viertelstunde erfasst: " + von + " bis " + bis);
    }
    if (!bis.isAfter(von)) {
      throw new IllegalArgumentException("Das Ende liegt nach dem Beginn: " + von + " bis " + bis);
    }
  }

  /**
   * Ein Eintrag, der nie geaendert und nie geloescht wurde — so entsteht jeder neue.
   *
   * @param id technische Kennung, oder {@code null} vor dem Speichern
   * @param accountId Kennung des Kontos, dem der Eintrag gehoert
   * @param angebotPositionId Kennung der Angebotsposition
   * @param tag der Tag, an dem gearbeitet wurde
   * @param von Beginn
   * @param bis Ende
   * @param createdAt Zeitpunkt der Anlage
   * @param updatedAt Zeitpunkt der letzten Aenderung
   */
  public Zeiteintrag(
      final @Nullable Long id,
      final long accountId,
      final long angebotPositionId,
      final LocalDate tag,
      final LocalTime von,
      final LocalTime bis,
      final Instant createdAt,
      final Instant updatedAt) {
    this(
        id,
        accountId,
        angebotPositionId,
        tag,
        von,
        bis,
        createdAt,
        updatedAt,
        0L,
        null,
        null,
        null);
  }

  /**
   * Der Eintrag mit dem Konto, das ihn gerade geaendert hat (Plan #415, E14).
   *
   * <p>Ein eigener Schritt wie {@code Angebotskommentar.durch(long)}: Wer aendert, ist eine Angabe
   * des Anwendungsfalls. Die Version bleibt, wie sie ist — sie zaehlt der Bestand beim Schreiben
   * weiter.
   *
   * @param konto Kennung des handelnden Kontos
   */
  public Zeiteintrag durch(final long konto) {
    return new Zeiteintrag(
        id,
        accountId,
        angebotPositionId,
        tag,
        von,
        bis,
        createdAt,
        updatedAt,
        version,
        konto,
        geloeschtAm,
        geloeschtVon);
  }

  /**
   * Der Eintrag mit dem Kennzeichen der Loeschung (Plan #415, E17).
   *
   * <p>Person, Position, Zeiten und Urheber der letzten Aenderung bleiben an der Zeile; nur
   * Zeitpunkt und Konto der Loeschung kommen hinzu.
   *
   * @param konto Kennung des loeschenden Kontos
   * @param zeitpunkt Zeitpunkt der Loeschung
   */
  public Zeiteintrag geloescht(final long konto, final Instant zeitpunkt) {
    return new Zeiteintrag(
        id,
        accountId,
        angebotPositionId,
        tag,
        von,
        bis,
        createdAt,
        updatedAt,
        version,
        geaendertVon,
        zeitpunkt,
        konto);
  }

  /**
   * Die Dauer des Eintrags in ganzen Minuten.
   *
   * <p>Immer ein Vielfaches von 15 und mindestens 15 — beides halten die Invarianten zu.
   */
  public long minuten() {
    return Duration.between(von, bis).toMinutes();
  }

  /** Die Dauer als Stunden mit zwei Nachkommastellen; bei Viertelstunden stets exakt (E5). */
  public BigDecimal stunden() {
    return stundenAus(minuten());
  }

  /**
   * Dieselbe Umrechnung fuer eine schon addierte Dauer.
   *
   * <p>Der Bestand summiert die Minuten seiner Zeilen und rechnet sie hier um, statt die Stunden
   * einzelner Eintraege zu addieren: So steht die Umrechnung an einer Stelle, und die Summe kann
   * nicht um Rundungsreste von der Dauer abweichen, aus der sie entstand.
   *
   * @param minuten die Dauer in ganzen Minuten, nicht negativ
   * @return die Dauer als Stunden mit Skala 2
   */
  public static BigDecimal stundenAus(final long minuten) {
    return BigDecimal.valueOf(minuten)
        .divide(MINUTEN_JE_STUNDE, NACHKOMMASTELLEN, RoundingMode.HALF_UP);
  }

  /**
   * Ob sich dieser Eintrag mit einem anderen zeitlich ueberschneidet (A8).
   *
   * <p><b>Beruehrende Grenzen ueberschneiden sich nicht:</b> 9:00 bis 10:00 und 10:00 bis 11:00
   * sind zwei Eintraege und kein Widerspruch. An verschiedenen Tagen gibt es keine Ueberschneidung,
   * auch bei gleicher Uhrzeit.
   *
   * <p><b>Dieselbe Person nicht zweimal, zwei Personen schon</b> (#410, Kriterium 5). Die Position
   * spielt keine Rolle: Wer zur selben Zeit fuer zwei Kunden bucht, hat sich verschrieben. Zwei
   * Eintraege verschiedener Konten ueberschneiden sich dagegen nie.
   *
   * <p>Welche Eintraege verglichen werden, entscheidet weiter der Anwendungsfall — die Methode
   * vergleicht zwei. Er liest dafuer nur die Eintraege der Person, der der gepruefte gehoeren soll
   * (Plan #426, E8); der Vergleich der Konten hier ist die letzte Sicherung hinter dieser Abfrage,
   * nicht die Regel selbst.
   *
   * @param anderer der Eintrag, gegen den verglichen wird
   */
  public boolean ueberschneidet(final Zeiteintrag anderer) {
    return accountId == anderer.accountId
        && tag.equals(anderer.tag)
        && von.isBefore(anderer.bis)
        && anderer.von.isBefore(bis);
  }

  /**
   * Ob eine Uhrzeit auf einer Viertelstunde liegt und keinen Bruchteil einer Sekunde traegt.
   *
   * <p>Die Sekunde wird getrennt von den Nanosekunden geprueft, weil {@link LocalTime} beides
   * einzeln fuehrt: 9:00:00.5 traegt die Sekunde 0 und kaeme sonst durch.
   *
   * <p><b>Oeffentlich, damit die Regel nur hier steht.</b> Der kompakte Konstruktor prueft sie als
   * Invariante und hat dafuer nur eine {@code IllegalArgumentException}; die Meldung am Feld, die
   * der Nutzer liest, kommt aus der Anwendungsschicht und muss darum <em>vor</em> dem Bau des
   * Eintrags fragen koennen (Plan #194, A19). Ohne diese Frage muesste die Anwendungsschicht die
   * Rechnung abschreiben, und zwei Abschriften derselben Regel liefen beim ersten Nachziehen
   * auseinander.
   *
   * @param zeit die zu pruefende Uhrzeit
   */
  public static boolean imRaster(final LocalTime zeit) {
    return zeit.getNano() == 0 && zeit.getSecond() == 0 && zeit.getMinute() % VIERTELSTUNDE == 0;
  }
}
