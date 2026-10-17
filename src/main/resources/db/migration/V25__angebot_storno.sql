-- fb.crm — Das Angebot laesst sich stornieren (Issue #333; Plan #332; fachliche Quelle #330).
--
-- E1 — „storniert" ist kein achter Status, sondern ein Kennzeichen neben ihm: Der Status behaelt
-- den Wert, aus dem storniert wurde, damit die Ruecknahme genau dorthin zurueckfuehrt (#330,
-- Kriterium 9) und der Jahresabschluss darauf rechnen kann (Kriterium 11). Der CHECK angebot_status
-- aus V20 bleibt darum, wie er ist.
--
-- Bestehende Zeilen sind nicht storniert und bleiben, was sie sind: storniert entsteht mit
-- DEFAULT false, storno_grund ohne DEFAULT, und kein UPDATE beruehrt eine Zeile.
--
-- E3 und E4 — Die vier CHECKs halten dieselben Zusagen wie der kompakte Konstruktor von Angebot,
-- je Zusage ein eigener CHECK mit eigenem Namen wie in V12: Welcher Name in der Verletzungsmeldung
-- steht, ist beobachtbar. Zwei Huerden fuer eine Regel ist hier Absicht, nach dem Muster von
-- angebot_art_status in V20 — die Domaene weist frueh und mit einer lesbaren Meldung ab, die
-- Datenbank laesst auch auf dem Weg daran vorbei (Migration, Konsole) keinen Widerspruch zu.
--
--   angebot_storno_grund_bindung  ein Grund steht nur an einem stornierten Angebot
--   angebot_storno_grund_text     ein Grund ist nicht leer — btrim mit dem Zeichensatz aus V12,
--                                 der eine Teilmenge dessen ist, was Javas strip() abschneidet
--   angebot_storno_grund_laenge   ein Grund hat hoechstens 2000 Zeichen, wie der Kommentar in V12
--   angebot_storno_status         storniert wird nur aus ANGELEGT, ABGEGEBEN und BESTELLT
--
-- Ein eigener CHECK gegen das Storno am internen Angebot fehlt mit Absicht: angebot_art_status
-- bindet intern an LAEUFT und ABGESCHLOSSEN, und beide schliesst angebot_storno_status schon aus.

ALTER TABLE angebot ADD COLUMN storniert boolean NOT NULL DEFAULT false;

ALTER TABLE angebot ADD COLUMN storno_grund text;

ALTER TABLE angebot
    ADD CONSTRAINT angebot_storno_grund_bindung CHECK (storno_grund IS NULL OR storniert);

ALTER TABLE angebot
    ADD CONSTRAINT angebot_storno_grund_text CHECK (
        storno_grund IS NULL OR btrim(storno_grund, E' \t\n\r\f\013') <> '');

ALTER TABLE angebot
    ADD CONSTRAINT angebot_storno_grund_laenge CHECK (
        storno_grund IS NULL OR length(storno_grund) <= 2000);

ALTER TABLE angebot
    ADD CONSTRAINT angebot_storno_status CHECK (
        NOT storniert OR status IN ('ANGELEGT', 'ABGEGEBEN', 'BESTELLT'));
