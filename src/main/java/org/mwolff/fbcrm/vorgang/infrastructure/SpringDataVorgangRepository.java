package org.mwolff.fbcrm.vorgang.infrastructure;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code vorgang}.
 *
 * <p><b>Eine</b> Abfrage traegt die Uebersicht und die Vorgangsliste einer Firma: Kriterium 2 und
 * Kriterium 12 verlangen dieselbe Reihenfolge, und eine Stelle haelt sie zusammen. Die Firmenliste
 * ist der Fall mit gesetztem {@code firmaId} und ohne Suchtext, die Uebersicht der umgekehrte.
 *
 * <p>Als native Abfrage, weil die Reihenfolge aus E16 eine Aggregation ueber die Historie braucht:
 * {@code COALESCE(max(geschehen_am), created_at) DESC} — juengstes Geschehen oben, ein Vorgang ohne
 * Eintrag mit seinem Anlagezeitpunkt. {@code vorgang.id DESC} haengt dahinter, damit zwei Aufrufe
 * bei gleichem Zeitpunkt dieselbe Reihenfolge liefern und der zuletzt angelegte oben steht.
 *
 * <p>Gesucht wird mit {@code position(... in ...)} und nicht mit {@code like}: Gesucht wird ein
 * Text, kein Muster — {@code %} und {@code _} im Suchtext treffen nur sich selbst (E17). Der
 * Leerstring steht an Position 1 jedes Titels und trifft damit jeden Vorgang, ohne dass es dafuer
 * einen zweiten Zweig braucht.
 *
 * <p>Die Nummer ist ein eigener Parameter und keine Ableitung aus dem Suchtext: Das fuehrende
 * {@code #} schneidet die Anwendungsschicht ab (E17). Ist sie {@code null}, ergibt der Vergleich
 * {@code = NULL} unbekannt und traegt zur Oder-Verknuepfung nichts bei — genau das ist gemeint.
 * Dasselbe gilt fuer {@code firmaId}: {@code null} heisst „ueber alle Firmen".
 *
 * <p>Jeder Wert kommt als gebundener Parameter, nie konkateniert (CLAUDE-security.md,
 * Datenbankzugriff). Die {@code cast}-Ausdruecke stehen dort, wo Postgres den Typ eines leeren
 * Platzhalters sonst nicht bestimmen kann, und ersetzen keine Bindung.
 */
interface SpringDataVorgangRepository extends JpaRepository<VorgangEntity, Long> {

  @Query(
      value =
          """
          SELECT v.* FROM vorgang v
            JOIN firma f ON f.id = v.firma_id
            LEFT JOIN vorgang_eintrag e ON e.vorgang_id = v.id
          WHERE (:auchAbgeschlossene = true OR v.abgeschlossen = false)
            AND (cast(:firmaId AS bigint) IS NULL OR v.firma_id = cast(:firmaId AS bigint))
            AND (position(lower(cast(:suche AS text)) in lower(v.titel)) > 0
                 OR position(lower(cast(:suche AS text)) in lower(f.name)) > 0
                 OR v.nummer = cast(:nummer AS bigint))
          GROUP BY v.id
          ORDER BY COALESCE(max(e.geschehen_am), v.created_at) DESC, v.id DESC
          """,
      nativeQuery = true)
  List<VorgangEntity> uebersicht(
      @Param("suche") String suche,
      @Param("nummer") @Nullable Long nummer,
      @Param("auchAbgeschlossene") boolean auchAbgeschlossene,
      @Param("firmaId") @Nullable Long firmaId);
}
