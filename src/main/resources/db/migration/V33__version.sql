-- fb.crm — Version und letzter Urheber je geschuetzter Tabelle (Issue #439; Plan #415, E1 und E19;
-- fachliche Quelle #411, Kriterien 1, 2 und 4).
--
-- Erstes Paket des Schutzes gegen gleichzeitiges Bearbeiten: Die Spalten entstehen in einem Zug,
-- bevor ein Bereich sie nutzt. Solange keine Entity sie kennt, aendert sich am Verhalten nichts.
--
-- Die Schritte und ihr Grund:
--
-- 1. version bigint NOT NULL DEFAULT 0 — E1: Ein eigener Zaehler erkennt, dass sich der Stand seit
--    dem Oeffnen geaendert hat; updated_at taugt dafuer nicht, weil zwei Aenderungen in derselben
--    Mikrosekunde denselben Stempel truegen. Das DEFAULT belegt jede bestehende Zeile mit 0, wie
--    storniert in V25 — darum braucht es kein UPDATE, und keine Zeile verliert etwas. Ein CHECK auf
--    version fehlt mit Absicht: Den Zaehler schreibt allein die Anwendung fort (an der Entity als
--    @Version, am Konto als Zaehler der Domaene nach E19), und er beginnt bei 0.
-- 2. geaendert_von bigint REFERENCES account (id) — wer zuletzt geaendert hat, damit die Meldung
--    bei einer Abweisung den Namen nennen kann (Kriterium 2). Die Spalte bleibt nullable: Fuer
--    den Bestand ist der Urheber unbekannt, und erfunden wird er nicht. Am Fremdschluessel steht
--    kein ON DELETE, wie an jedem Fremdschluessel dieses Schemas — Konten werden stillgelegt, nicht
--    geloescht; aus demselben Grund traegt die Spalte keinen eigenen Index.
--
-- Die elf Tabellen sind die Datensaetze aus Kriterium 4. Die Anlage am Angebot fehlt mit Absicht:
-- Sie aendert sich nie, ihr einziger schreibender Weg ist das Loeschen (E7, V34). Die Positionen
-- des Angebots zaehlen zum Angebot, das als Ganzes eine Version traegt. account erhaelt dieselben
-- Spalten; dass Anmelden und Abmelden die Version stehen lassen, regelt die Anwendung (E19).
--
-- Die Migration verliert keine Daten. Der Weg zurueck ist die Sicherung, die docs/betrieb.md unter
-- „Aktualisierung" vor jedem Update verlangt, oder je Tabelle
-- ALTER TABLE <tabelle> DROP COLUMN geaendert_von, DROP COLUMN version.

ALTER TABLE angebot
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN geaendert_von bigint REFERENCES account (id);

ALTER TABLE angebot_kommentar
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN geaendert_von bigint REFERENCES account (id);

ALTER TABLE rechnung
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN geaendert_von bigint REFERENCES account (id);

ALTER TABLE rechnung_nachgetragen
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN geaendert_von bigint REFERENCES account (id);

ALTER TABLE arbeitszeit
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN geaendert_von bigint REFERENCES account (id);

ALTER TABLE firma
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN geaendert_von bigint REFERENCES account (id);

ALTER TABLE ansprechpartner
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN geaendert_von bigint REFERENCES account (id);

ALTER TABLE eigene_angaben
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN geaendert_von bigint REFERENCES account (id);

ALTER TABLE rechnung_einstellungen
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN geaendert_von bigint REFERENCES account (id);

ALTER TABLE briefbogen
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN geaendert_von bigint REFERENCES account (id);

ALTER TABLE account
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN geaendert_von bigint REFERENCES account (id);
