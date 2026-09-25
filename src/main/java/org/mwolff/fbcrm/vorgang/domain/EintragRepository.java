package org.mwolff.fbcrm.vorgang.domain;

import java.util.List;
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
}
