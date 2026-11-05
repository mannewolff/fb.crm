-- fb.crm — Die Angebotsposition bekommt freiwillig den Monat ihrer Leistung (Plan #484; fachliche
-- Quelle #483).
--
-- Der Forecast der Startseite ordnet die Positionen eines Jahres nach dem Monat ihrer Leistung
-- (#483, Kriterium 4). Dafuer traegt jede Position neben ihrem Leistungsjahr einen Monat als
-- eigene Spalte, 1 fuer Januar bis 12 fuer Dezember.
--
-- A1 — Der Monat ist freiwillig (#483, Kriterium 2): NULL heisst „ohne Monat". Anders als beim
-- Leistungsjahr in V23 gibt es darum weder einen Fuellschritt noch NOT NULL. Bestehende Positionen
-- bleiben ohne Monat, bis jemand ihnen von Hand einen gibt (Kriterium 7). Ein DEFAULT entsteht
-- ebenfalls nicht: Eine neue Position steht auf „ohne Monat", und das ist NULL.
--
-- Die Grenze 1 bis 12 steht nach dem Muster von angebot_position_leistungsjahr hier und an der
-- Anfrage (A7). Die Luecken bei V36 und V38 bleiben (E1).

ALTER TABLE angebot_position ADD COLUMN leistungsmonat smallint;

ALTER TABLE angebot_position
    ADD CONSTRAINT angebot_position_leistungsmonat CHECK (leistungsmonat BETWEEN 1 AND 12);
