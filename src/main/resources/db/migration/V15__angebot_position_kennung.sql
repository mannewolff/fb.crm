-- fb.crm — Die Angebotsposition bekommt eine dauerhafte Kennung (Plan #169, E2; Issue #171).
--
-- Bis hierher schrieb JpaAngebotRepository.save die Positionen eines Angebots neu: alle Zeilen
-- loeschen, alle Zeilen einfuegen. Die Kennung einer Zeile war damit ein Zufall des letzten
-- Speicherns, und eine Rechnung koennte sich nicht auf eine Position ihres Angebots berufen
-- (#160, Kriterium 28). Von hier an werden die Zeilen fortgeschrieben — mit ihrer Kennung.
--
-- Die Spalte id gibt es schon (V6__angebot.sql, GENERATED ALWAYS AS IDENTITY); diese Migration
-- aendert nur, was dem Fortschreiben im Weg stand: Die Eindeutigkeit ueber (angebot_id, position)
-- pruefte nach jeder einzelnen Anweisung. Werden die Plaetze 1 und 2 zweier Positionen getauscht,
-- steht nach dem ersten UPDATE zwangslaeufig zweimal derselbe Platz da — der Tausch scheiterte,
-- ohne dass am Ende der Transaktion etwas falsch waere. Aufgeschoben prueft die Datenbank erst beim
-- Commit: Der Zwischenstand darf sich widersprechen, das Ergebnis nicht.
--
-- Postgres 16 kann einen bestehenden UNIQUE-Constraint nicht nachtraeglich aufschiebbar machen —
-- ALTER TABLE … ALTER CONSTRAINT gilt nur fuer Fremdschluessel. Er wird darum fallen gelassen und
-- in derselben Anweisung neu angelegt; so ist die Tabelle zu keinem Zeitpunkt ohne ihn.
ALTER TABLE angebot_position
    DROP CONSTRAINT angebot_position_reihenfolge,
    ADD CONSTRAINT angebot_position_reihenfolge UNIQUE (angebot_id, position) DEFERRABLE INITIALLY DEFERRED;
