-- fb.crm — Verfasser am Kommentar (Issue #420; Plan #414, A7, E7, E13; fachliche Quelle #409,
-- Kriterium 11).
--
-- Eine eigene Migration neben V30 und nicht deren Teil: Spalte und Entity entstehen im selben
-- Paket, sonst scheiterte jedes Speichern eines Kommentars am CHECK unten.
--
-- Die Schritte in ihrer Reihenfolge und ihr Grund:
--
-- 1. verfasser_id verweist auf das Konto, ohne ON DELETE: Konten werden stillgelegt, nicht
--    geloescht, und der Fremdschluessel sichert das ab (A7). Der Name steht bewusst nicht in der
--    Zeile — Kriterium 11 verlangt den aktuellen Namen, und den liest die Anwendung beim Lesen
--    aus dem Konto.
-- 2. Die vorhandenen Kommentare gehoeren dem bisherigen Konto (E7). Bis zu dieser Migration gibt
--    es hoechstens eines; min(id) trifft es. Ohne Konto bleibt die Spalte leer.
-- 3. Statt SET NOT NULL ein CHECK mit NOT VALID (E13): Er bindet jeden neuen und jeden geaenderten
--    Kommentar, laesst aber einen Bestand mit Kommentaren und ohne Konto durchlaufen. Dessen
--    Kommentare uebernimmt die Einrichtung fuer das erste Konto (ErstesKontoFolge). Der CHECK
--    bleibt NOT VALID; die Anwendung aendert kein Schema.
-- 4. Der Index traegt den Fremdschluessel: Ohne ihn suchte Postgres bei jeder Pruefung am Konto
--    die ganze Tabelle ab, und die Uebernahme der Einrichtung findet die leeren Zeilen ueber ihn.
--
-- Die Migration verliert keine Daten. Der Weg zurueck ist die Sicherung, die docs/betrieb.md unter
-- „Aktualisierung" vor jedem Update verlangt.

ALTER TABLE angebot_kommentar ADD COLUMN verfasser_id bigint REFERENCES account (id);

UPDATE angebot_kommentar SET verfasser_id = (SELECT min(id) FROM account);

ALTER TABLE angebot_kommentar
    ADD CONSTRAINT angebot_kommentar_verfasser CHECK (verfasser_id IS NOT NULL) NOT VALID;

CREATE INDEX angebot_kommentar_verfasser_idx ON angebot_kommentar (verfasser_id);
