package org.mwolff.fbcrm.firma.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code firma}.
 *
 * <p>Die Uebersicht sucht mit {@code position(... in ...)} und nicht mit {@code like}: Gesucht wird
 * ein Text, kein Muster — {@code %} und {@code _} im Suchtext treffen nur sich selbst (E5). Der
 * Leerstring steht an Position 1 jedes Namens und trifft damit jede Firma, ohne dass es dafuer
 * einen zweiten Zweig braucht.
 *
 * <p>Sortiert wird nach {@code lower(name)} und bei gleichem Namen nach {@code id} — sonst
 * tauschten zwei gleichnamige Firmen zwischen zwei Aufrufen die Plaetze (E6). Beide Werte kommen
 * als gebundene Parameter, nie konkateniert (CLAUDE-security.md).
 */
interface SpringDataFirmaRepository extends JpaRepository<FirmaEntity, Long> {

  @Query(
      """
      select f from FirmaEntity f
      where (:auchStillgelegte = true or f.aktiv = true)
        and position(lower(:suche) in lower(f.name)) > 0
      order by lower(f.name), f.id
      """)
  List<FirmaEntity> uebersicht(
      @Param("suche") String suche, @Param("auchStillgelegte") boolean auchStillgelegte);
}
