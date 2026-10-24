-- fb.crm — Loeschkennzeichen statt Entfernen (Issue #439; Plan #415, E7 und E17; fachliche Quelle
-- #411, Kriterium 6).
--
-- Wer einen inzwischen geloeschten Eintrag bearbeitet, soll erfahren, wer ihn wann geloescht hat.
-- Ist die Zeile fort, ist auch diese Auskunft fort. Darum werden Kommentar, Rechnungsentwurf,
-- nachgetragene Rechnung, Zeiteintrag und die Anlage am Angebot kuenftig beim Loeschen nur
-- gekennzeichnet und aus allen Lesewegen genommen (E17, fuer die Anlage E7). Eine eigene
-- Papierkorbtabelle je Fachtabelle ist verworfen: Die Kennzeichnung an der Zeile ist der kleinere
-- Eingriff und laesst die Fremdschluessel unberuehrt.
--
-- Die Schritte und ihr Grund:
--
-- 1. geloescht_am timestamptz — der Zeitpunkt der Loeschung; NULL heisst „nicht geloescht". Ohne
--    DEFAULT: Jede bestehende Zeile ist danach nicht geloescht, ohne UPDATE.
-- 2. geloescht_von bigint REFERENCES account (id) — wer geloescht hat. Nullable, damit die
--    Kennzeichnung auch ohne bekannten Urheber haelt; ohne ON DELETE wie jeder Fremdschluessel
--    dieses Schemas.
-- 3. CHECK <tabelle>_geloescht_bindung — ein Urheber der Loeschung steht nur an einer geloeschten
--    Zeile. Je Tabelle ein eigener Name, damit die Verletzungsmeldung die Tabelle nennt (wie V25).
--
-- Die Migration verliert keine Daten. Der Weg zurueck ist die Sicherung, die docs/betrieb.md unter
-- „Aktualisierung" vor jedem Update verlangt, oder je Tabelle ALTER TABLE <tabelle> DROP CONSTRAINT
-- <tabelle>_geloescht_bindung, DROP COLUMN geloescht_von, DROP COLUMN geloescht_am — nach V35
-- zuerst dessen Rueckweg.

ALTER TABLE angebot_kommentar
    ADD COLUMN geloescht_am timestamptz,
    ADD COLUMN geloescht_von bigint REFERENCES account (id),
    ADD CONSTRAINT angebot_kommentar_geloescht_bindung
        CHECK (geloescht_von IS NULL OR geloescht_am IS NOT NULL);

ALTER TABLE angebot_anlage
    ADD COLUMN geloescht_am timestamptz,
    ADD COLUMN geloescht_von bigint REFERENCES account (id),
    ADD CONSTRAINT angebot_anlage_geloescht_bindung
        CHECK (geloescht_von IS NULL OR geloescht_am IS NOT NULL);

ALTER TABLE rechnung
    ADD COLUMN geloescht_am timestamptz,
    ADD COLUMN geloescht_von bigint REFERENCES account (id),
    ADD CONSTRAINT rechnung_geloescht_bindung
        CHECK (geloescht_von IS NULL OR geloescht_am IS NOT NULL);

ALTER TABLE rechnung_nachgetragen
    ADD COLUMN geloescht_am timestamptz,
    ADD COLUMN geloescht_von bigint REFERENCES account (id),
    ADD CONSTRAINT rechnung_nachgetragen_geloescht_bindung
        CHECK (geloescht_von IS NULL OR geloescht_am IS NOT NULL);

ALTER TABLE arbeitszeit
    ADD COLUMN geloescht_am timestamptz,
    ADD COLUMN geloescht_von bigint REFERENCES account (id),
    ADD CONSTRAINT arbeitszeit_geloescht_bindung
        CHECK (geloescht_von IS NULL OR geloescht_am IS NOT NULL);
