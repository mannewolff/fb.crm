package org.mwolff.fbcrm.auftrag.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Port auf den Bestand der Auftraege; die Umsetzung liegt in {@code auftrag.infrastructure}.
 *
 * <p>Der Auftrag ist mit seinen Positionen <b>eine</b> Einheit: Gelesen wird er mit ihnen in ihrer
 * Reihenfolge, geschrieben wird er mit der vollstaendigen Liste, und die Plaetze vergibt der
 * Adapter lueckenlos ab 1 aus der Reihenfolge der Liste. Einzelne Positionen anzulegen, zu aendern
 * oder zu verschieben gibt es deshalb nicht — nach dem Anlegen ohnehin nicht mehr (F3).
 */
public interface AuftragRepository {

  /** Der Auftrag zu einer technischen Id samt seinen Positionen, oder leer. */
  Optional<Auftrag> findById(long id);

  /**
   * Alle Auftraege eines Vorgangs, jeder mit seinen Positionen.
   *
   * <p>Ohne zugesagte Reihenfolge: Welche Ordnung die Ansicht zeigt, entscheidet Kriterium 9 und
   * damit die Anwendungsschicht (E13) — der Bestand liefert den Inhalt, nicht die Darstellung.
   *
   * @param vorgangId Kennung des Vorgangs
   */
  List<Auftrag> findByVorgang(long vorgangId);

  /**
   * Der Auftrag zu einem Angebot samt seinen Positionen, oder leer.
   *
   * <p>Hoechstens einer, und das haelt die Datenbank mit {@code UNIQUE} auf {@code angebot_id} fest
   * (F9) — deshalb ein {@link Optional} und keine Liste.
   *
   * @param angebotId Kennung des Angebots, aus dem der Auftrag entstand
   */
  Optional<Auftrag> findByAngebot(long angebotId);

  /**
   * Die Kennungen derjenigen Vorgaenge aus {@code vorgangIds}, an denen mindestens ein Auftrag
   * haengt.
   *
   * <p>Die Auswahl ist sein blosses Dasein und kein Zustand: Ein Auftrag hat keinen Entwurf und
   * traegt seine Nummer ab dem Anlegen — es gibt keine Lage, in der er als Beleg nicht zaehlt (F6).
   * Beim Angebot ist das anders, und darum steht die Regel je Belegart in ihrer eigenen Umsetzung
   * des Ports und nicht am Port.
   *
   * <p>Nur die Kennungen, nicht die Auftraege: Der Aufrufer will wissen, <b>ob</b> einer haengt,
   * und ganze Auftraege mit ihren Positionen zu laden waere Gewicht ohne Nutzen.
   *
   * @param vorgangIds die Kennungen der gefragten Vorgaenge; nie leer — der Aufrufer faengt den
   *     Fall ab, weil {@code in ()} kein gueltiges SQL ist
   */
  Set<Long> vorgaengeMitAuftrag(Collection<Long> vorgangIds);

  /** Legt den Auftrag an oder schreibt ihn fort und liefert ihn mit gesetzter Id zurueck. */
  Auftrag save(Auftrag auftrag);

  /**
   * Loescht den Auftrag samt seinen Positionen (Kriterium 15).
   *
   * <p>Echt und ohne Statusspur: Es gibt keinen Status „geloescht", und die Auftragsnummer bleibt
   * verbraucht (E6). Ob geloescht werden darf, entscheidet der Anwendungsfall vor dem Aufruf.
   *
   * @param id Kennung des zu loeschenden Auftrags
   */
  void loesche(long id);
}
