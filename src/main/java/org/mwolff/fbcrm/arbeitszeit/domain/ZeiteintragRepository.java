package org.mwolff.fbcrm.arbeitszeit.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Port auf den Bestand der Zeiteintraege; die Umsetzung liegt in {@code
 * arbeitszeit.infrastructure}.
 *
 * <p>Jeder Zeiteintrag ist eine Zeile fuer sich — wie der Kommentar am Angebot und anders als die
 * Positionen eines Belegs: Er traegt seine eigene Kennung und wird einzeln angelegt,
 * fortgeschrieben und geloescht (Plan #194, A3).
 *
 * <p><b>Zwei Summen und kein dritter Weg.</b> Beide liefern Stunden je Angebotsposition, und beide
 * nehmen alle Positionen auf einmal entgegen, statt je Zeile einmal zu fragen — dasselbe Muster wie
 * {@code firma.domain.AnsprechpartnerRepository#zaehleAktiveJeFirma}. Sie unterscheiden sich nur im
 * Zeitraum: {@link #angefallenJePosition(Set)} zaehlt alles, {@link #stundenJePositionImMonat(Set,
 * YearMonth)} nur einen Monat.
 */
public interface ZeiteintragRepository {

  /** Der Zeiteintrag zu einer technischen Kennung, oder leer. */
  Optional<Zeiteintrag> findById(long id);

  /**
   * Alle Zeiteintraege in einem Zeitraum, beide Grenzen eingeschlossen.
   *
   * <p>Ueber alle Positionen, denn beide Leser brauchen genau das: die Monatsliste der Ansicht
   * (Issue #193, Kriterium 5) und die Ueberschneidungspruefung eines einzelnen Tages, die niemanden
   * zur selben Zeit fuer zwei Kunden arbeiten laesst (Kriterium 4, A8). Fuer den einzelnen Tag
   * stehen in beiden Grenzen derselbe Tag — ein eigenes {@code findAmTag} waere dieselbe Abfrage
   * unter zweitem Namen.
   *
   * <p>Ohne zugesagte Reihenfolge, wie die Listen der Belege: Welche Ordnung die Ansicht zeigt,
   * entscheidet die Anwendungsschicht.
   *
   * @param von erster Tag des Zeitraums
   * @param bis letzter Tag des Zeitraums
   */
  List<Zeiteintrag> findImZeitraum(LocalDate von, LocalDate bis);

  /**
   * Legt den Zeiteintrag an oder schreibt ihn fort und liefert ihn mit gesetzter Kennung zurueck.
   */
  Zeiteintrag save(Zeiteintrag zeiteintrag);

  /**
   * Loescht den Zeiteintrag — die Zeile ist danach fort.
   *
   * <p>Loeschen ist immer erlaubt, auch an einem schon abgerechneten Angebot (Plan #194, A7): Wer
   * sich verschrieben hat, soll den Fehler wegnehmen koennen. Dass der Eintrag existiert, prueft
   * der Anwendungsfall; eine unbekannte Kennung ist hier kein Fehler.
   *
   * @param id Kennung des Zeiteintrags
   */
  void delete(long id);

  /**
   * Die insgesamt erfassten Stunden je Angebotsposition (Issue #193, Kriterium 7).
   *
   * <p>Ueber alle Monate: Das ist die Spalte „Angefallen" am Angebot, die auch nach dem Sprung auf
   * „abgerechnet" stehen bleibt.
   *
   * @param angebotPositionIds die Kennungen der Positionen
   * @return je angefragte Position ihre Stunden; eine Position ohne Eintrag steht mit {@code 0.00}
   *     darin, keine fehlt
   */
  Map<Long, BigDecimal> angefallenJePosition(Set<Long> angebotPositionIds);

  /**
   * Dasselbe, aber nur fuer einen Monat (Issue #193, Kriterium 9).
   *
   * <p>Das ist der Vorschlag im Rechnungsentwurf: Wer eine Rechnung fuer November anlegt, bekommt
   * die Novemberstunden als Menge.
   *
   * @param angebotPositionIds die Kennungen der Positionen
   * @param monat der Monat, dessen Eintraege zaehlen
   * @return je angefragte Position ihre Stunden in diesem Monat; eine Position ohne Eintrag steht
   *     mit {@code 0.00} darin, keine fehlt
   */
  Map<Long, BigDecimal> stundenJePositionImMonat(Set<Long> angebotPositionIds, YearMonth monat);
}
