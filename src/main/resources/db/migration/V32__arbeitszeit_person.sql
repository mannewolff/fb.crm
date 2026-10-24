-- fb.crm — Person am Zeiteintrag (Issue #429; Plan #426, E1, E3, E4, E8, E9; fachliche Quelle
-- #410, Kriterien 1 und 5).
--
-- Jeder Zeiteintrag gehoert kuenftig einer Person: account_id verweist auf das Konto, ohne
-- Beziehung in JPA und ohne Namen in der Zeile (E1) — den aktuellen Namen liest die Anwendung aus
-- dem Konto. Am Fremdschluessel steht kein ON DELETE, wie an jedem Fremdschluessel dieses Schemas:
-- Konten werden stillgelegt, nicht geloescht.
--
-- E3 — Die Schritte in ihrer Reihenfolge und ihr Grund:
--
-- 1. Die Spalte entsteht nullable, sonst liesse sie sich an einem Bestand mit Zeilen nicht anlegen.
-- 2. Die vorhandenen Eintraege gehoeren dem aeltesten Konto (Kriterium 1: dem bisherigen Konto).
--    Auf einer Installation nach der bisherigen Betriebsform ist das das einzige. Auf einer
--    Installation ohne Konto ist arbeitszeit leer — ohne Anmeldung entsteht kein Eintrag —, der
--    UPDATE trifft keine Zeile.
-- 3. Danach ist die Spalte Pflicht. Ein Vorgabewert in der Spaltendefinition oder ein Lauf in Java
--    beim Start waeren verworfen: Drei Schritte in dieser einen Datei sind in sich geschlossen und
--    ohne Anwendungslauf nachpruefbar. Ein Bestand mit Zeiteintraegen und ohne Konto — denkbar nur
--    nach dem frueheren Rueckweg „Kontozeile loeschen" und vor der erneuten Einrichtung — laesst
--    diesen Schritt scheitern; die Migration laeuft in einer Transaktion und hinterlaesst dann
--    nichts.
--
-- E9 — Der Index (account_id, tag) traegt die Monatsliste einer Person und die
-- Ueberschneidungspruefung je Person. arbeitszeit_tag_idx und arbeitszeit_angebot_position_idx
-- bleiben: Die Sicht „Alle" und die Monate mit Eintrag lesen weiter nur ueber den Tag, und ein
-- zusammengesetzter Index mit account_id an erster Stelle bedient diesen Weg nicht.
--
-- E8 — Die Ueberschneidung zweier Eintraege prueft weiterhin NICHT die Datenbank: Nur die
-- Anwendungsschicht kennt den kollidierenden Eintrag, den die Meldung nennen muss — eine
-- Ausschlussbedingung lieferte einen Fehler ohne diesen Bezug. Sie gilt jetzt je Person.
--
-- Die Migration verliert keine Daten. Der Weg zurueck ist die Sicherung, die docs/betrieb.md unter
-- „Aktualisierung" vor jedem Update verlangt, oder ALTER TABLE arbeitszeit DROP COLUMN account_id.

ALTER TABLE arbeitszeit ADD COLUMN account_id bigint REFERENCES account (id);

UPDATE arbeitszeit SET account_id = (SELECT id FROM account ORDER BY id LIMIT 1);

ALTER TABLE arbeitszeit ALTER COLUMN account_id SET NOT NULL;

CREATE INDEX arbeitszeit_account_tag_idx ON arbeitszeit (account_id, tag);
