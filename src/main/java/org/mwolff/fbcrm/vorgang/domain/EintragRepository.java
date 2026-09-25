package org.mwolff.fbcrm.vorgang.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port auf den Bestand der Historieneintraege; die Umsetzung liegt in {@code
 * vorgang.infrastructure}.
 */
public interface EintragRepository {

  /**
   * Die vollstaendige Historie eines Vorgangs — Kommentare und Anhaenge als <b>eine</b> Folge (E6)
   * —, sortiert nach dem Zeitpunkt des Geschehens absteigend und bei gleichem Zeitpunkt nach Id
   * absteigend, also der zuletzt erfasste oben.
   *
   * <p>Ohne Seitenteilung: Die Detailansicht zeigt die Historie in einem Stueck (E25).
   *
   * @param vorgangId Kennung des Vorgangs
   */
  List<Eintrag> findByVorgang(long vorgangId);

  /** Der Eintrag zu einer technischen Id, oder leer. */
  Optional<Eintrag> findById(long id);

  /** Legt den Eintrag an oder schreibt ihn fort und liefert ihn mit gesetzter Id zurueck. */
  Eintrag save(Eintrag eintrag);

  /**
   * Der Zeitpunkt des juengsten Geschehens je Vorgang — der Tag, den die Uebersicht je Zeile zeigt
   * (Kriterium 2).
   *
   * <p>Die Zahl der Abfragen haengt nicht an der Zahl der Zeilen: Die Uebersicht fragt einmal fuer
   * alle gefundenen Vorgaenge, nicht einmal je Vorgang.
   *
   * <p><b>Ein Vorgang ohne Eintrag steht nicht in der Abbildung.</b> Anders als bei der Zaehlung
   * der Ansprechpartner gibt es hier keinen neutralen Wert — der Nullpunkt der Zeitrechnung waere
   * kein Ersatz, sondern eine Behauptung. Der Aufrufer setzt dafuer den Anlagezeitpunkt des
   * Vorgangs, und nur er kennt ihn.
   *
   * @param vorgangIds Kennungen der Vorgaenge, nach denen gefragt wird
   */
  Map<Long, Instant> juengstesGeschehenJeVorgang(Collection<Long> vorgangIds);
}
