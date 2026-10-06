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
 * <p><b>Vier Summen in zwei Paaren und ein fuenfter Weg ohne Stunden.</b> Die vier Summen liefern
 * Stunden je Angebotsposition, und keine fragt je Zeile einmal — dasselbe Muster wie {@code
 * firma.domain.AnsprechpartnerRepository#zaehleAktiveJeFirma}. Sie unterscheiden sich in zwei
 * Richtungen:
 *
 * <ul>
 *   <li><b>Mit Positionsmenge</b> ({@link #angefallenJePosition(Set)}, {@link
 *       #stundenJePositionImMonat(Set, YearMonth)}): Gefragt wird nach bestimmten Positionen, und
 *       jede angefragte steht in der Antwort — auch die ohne Eintrag, mit {@code 0.00}. Das ist der
 *       Weg, wenn der Leser ein Angebot im Blick hat.
 *   <li><b>Ohne Positionsmenge</b> ({@link #alleAngefallenJePosition()}, {@link
 *       #alleStundenJePositionImZeitraum(LocalDate, LocalDate)}): Gefragt wird nach nichts,
 *       geantwortet wird mit dem ganzen Bestand — darum steht darin <b>nur</b> eine Position, zu
 *       der es einen Eintrag gibt, und keine mit {@code 0.00}. Das ist der Weg, wenn der Leser alle
 *       Angebote meint (Issue #211) und ein Aufruf je Angebot die Abfragelawine waere, die dieses
 *       Modul vermeidet (Plan #208, E3).
 * </ul>
 *
 * <p>Quer dazu steht der Zeitraum: je Paar einmal ueber alle Monate und einmal ueber einen
 * begrenzten — mit Positionsmenge genau ein Monat, ohne sie zwei Datumsgrenzen, die einen Monat
 * ebenso fassen wie ein Jahr (Plan #274, E6).
 *
 * <p>Der fuenfte Weg, {@link #monateMitEintragImZeitraum(LocalDate, LocalDate)}, ist der eine, der
 * keine Stunden liefert: Er sagt nur, in welchen Monaten eines Zeitraums ueberhaupt Zeit erfasst
 * ist.
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

  /**
   * Die insgesamt erfassten Stunden je Angebotsposition ueber <b>alle</b> Angebote (Issue #211).
   *
   * <p>Ohne Positionsmenge: Der Leser meint alle Angebote, und ein Aufruf je Angebot waere die
   * Abfragelawine, die dieses Modul vermeidet (Plan #208, E3).
   *
   * @return je Position mit mindestens einem Eintrag ihre Stunden; eine Position ohne Eintrag
   *     <b>fehlt</b> — anders als bei {@link #angefallenJePosition(Set)}, wo sie mit {@code 0.00}
   *     darin steht, weil dort nach ihr gefragt wurde
   */
  Map<Long, BigDecimal> alleAngefallenJePosition();

  /**
   * Dasselbe, aber nur fuer einen Zeitraum, beide Grenzen eingeschlossen (Issue #211; Plan #274,
   * E6).
   *
   * <p>Zwei Datumsgrenzen statt eines Monats: So fasst derselbe Weg einen Monat wie ein ganzes
   * Jahr, und dieses Modul lernt keinen Begriff seines Lesers.
   *
   * @param von erster Tag des Zeitraums
   * @param bis letzter Tag des Zeitraums
   * @return je Position mit mindestens einem Eintrag im Zeitraum ihre Stunden; eine Position ohne
   *     Eintrag <b>fehlt</b> — anders als bei {@link #stundenJePositionImMonat(Set, YearMonth)}, wo
   *     sie mit {@code 0.00} darin steht, weil dort nach ihr gefragt wurde
   */
  Map<Long, BigDecimal> alleStundenJePositionImZeitraum(LocalDate von, LocalDate bis);

  /**
   * Die Monate eines Zeitraums, in denen mindestens ein Zeiteintrag liegt, beide Grenzen
   * eingeschlossen (Issue #273, Kriterium 2).
   *
   * <p>Der eine Weg, der keine Stunden liefert: Gefragt wird nur, ob in einem Monat Zeit erfasst
   * ist, nicht wie viel.
   *
   * @param von erster Tag des Zeitraums
   * @param bis letzter Tag des Zeitraums
   * @return jeder Monat mit mindestens einem Eintrag im Zeitraum, je einmal; ein Monat ohne Eintrag
   *     fehlt
   */
  Set<YearMonth> monateMitEintragImZeitraum(LocalDate von, LocalDate bis);
}
