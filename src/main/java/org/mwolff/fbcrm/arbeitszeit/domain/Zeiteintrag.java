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
 * Kriterium 1).
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
 * @param id technische Kennung — {@code null}, solange der Eintrag nicht gespeichert ist
 * @param angebotPositionId Kennung der Angebotsposition, auf die gebucht wurde
 * @param tag der Tag, an dem gearbeitet wurde
 * @param von Beginn, auf einer Viertelstunde ohne Sekunden
 * @param bis Ende, auf einer Viertelstunde ohne Sekunden; stets nach {@code von}
 * @param createdAt Zeitpunkt der Anlage
 * @param updatedAt Zeitpunkt der letzten Aenderung
 */
public record Zeiteintrag(
    @Nullable Long id,
    long angebotPositionId,
    LocalDate tag,
    LocalTime von,
    LocalTime bis,
    Instant createdAt,
    Instant updatedAt)
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
   * <p>Die Position spielt keine Rolle: Niemand arbeitet zur selben Zeit fuer zwei Kunden (Issue
   * #193, Kriterium 4). Welche Eintraege verglichen werden, entscheidet der Anwendungsfall — die
   * Methode vergleicht zwei.
   *
   * @param anderer der Eintrag, gegen den verglichen wird
   */
  public boolean ueberschneidet(final Zeiteintrag anderer) {
    return tag.equals(anderer.tag) && von.isBefore(anderer.bis) && anderer.von.isBefore(bis);
  }

  /*
   * Auf einer Viertelstunde und ohne Bruchteil einer Sekunde. Die Sekunde wird getrennt von den
   * Nanosekunden geprueft, weil LocalTime beides einzeln fuehrt: 9:00:00.5 traegt die Sekunde 0
   * und kaeme sonst durch.
   */
  private static boolean imRaster(final LocalTime zeit) {
    return zeit.getNano() == 0 && zeit.getSecond() == 0 && zeit.getMinute() % VIERTELSTUNDE == 0;
  }
}
