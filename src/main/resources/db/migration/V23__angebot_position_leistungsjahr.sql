-- fb.crm — Die Angebotsposition bekommt das Jahr ihrer Leistung (Plan #303; fachliche Quelle #302).
--
-- Der Forecast der Startseite ordnet jede offene Position dem Jahr zu, in dem die Leistung erbracht
-- wird, und nicht dem Jahr des Angebotsdatums (#302, Kriterium 4). Dafuer traegt jede Position ihr
-- Leistungsjahr als eigene Spalte.
--
-- E11 — Kriterium 8 verlangt, dass jede bestehende Position sofort im Forecast steht und es keine
-- Gruppe „ohne Jahr" gibt. Die Spalte entsteht darum in drei Schritten:
--   1. Sie wird nullable angelegt — die bestehenden Zeilen haben noch keinen Wert.
--   2. Jede bestehende Position bekommt das Jahr des Angebotsdatums ihres Angebots.
--   3. Danach wird sie NOT NULL. Eine nullable Spalte waere ein Fehlerpfad in jeder Rechnung
--      darueber; nach Schritt 2 gibt es keine Zeile mehr, die daran scheitern koennte.
-- Ein DEFAULT entsteht dabei nicht: Das Jahr einer neuen Position setzt der Anwendungsfall aus dem
-- Angebotsdatum (E2), und ein fester Wert in der Datenbank waere eine zweite, falsche Vorbelegung.
--
-- E10 — Erlaubt sind die Jahre 2000 bis 2099. „20267" ist ein Tippfehler und keine Angabe; die
-- Grenze steht nach dem Muster von angebot_position_menge hier und an der Anfrage.

ALTER TABLE angebot_position ADD COLUMN leistungsjahr smallint;

UPDATE angebot_position p
   SET leistungsjahr = extract(year from a.angebot_datum)
  FROM angebot a
 WHERE a.id = p.angebot_id;

ALTER TABLE angebot_position ALTER COLUMN leistungsjahr SET NOT NULL;

ALTER TABLE angebot_position
    ADD CONSTRAINT angebot_position_leistungsjahr CHECK (leistungsjahr BETWEEN 2000 AND 2099);
