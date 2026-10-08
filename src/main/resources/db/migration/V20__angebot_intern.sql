-- fb.crm — Das Angebot bekommt das Kennzeichen „intern" und zwei eigene Status (Issue #226).
--
-- Dieselbe Mappe haelt kuenftig auch die eigene interne Arbeit fest (Plan #218, fachliche Quelle
-- #207, Kriterium 1). Die Art steht als eigene Spalte neben dem Status und nicht nur in ihm (E1):
-- Eine Abfrage nach der Art soll ohne Aufzaehlung von Statuswerten gehen.
--
-- Bestehende Zeilen sind Angebote an Kunden und bleiben, was sie sind: Die Spalte entsteht mit
-- DEFAULT false, und kein UPDATE verschiebt einen Status.
--
-- Der CHECK angebot_art_status haelt dieselbe Zusage wie der kompakte Konstruktor von Angebot (E3):
-- Das Kennzeichen und der Status sagen dasselbe. Zwei Huerden fuer eine Regel ist hier Absicht —
-- die Domaene weist frueh und mit einer lesbaren Meldung ab, die Datenbank laesst auch auf dem Weg
-- daran vorbei (Migration, Konsole) keine widerspruechliche Zeile zu.

ALTER TABLE angebot ADD COLUMN intern boolean NOT NULL DEFAULT false;

-- Der CHECK aus V11 nannte fuenf Werte; die zwei Status der internen Arbeit kommen dazu.
ALTER TABLE angebot DROP CONSTRAINT angebot_status;

ALTER TABLE angebot
    ADD CONSTRAINT angebot_status CHECK (
        status IN ('ANGELEGT', 'ABGEGEBEN', 'BESTELLT', 'ERLEDIGT', 'ABGERECHNET',
                   'LAEUFT', 'ABGESCHLOSSEN'));

ALTER TABLE angebot
    ADD CONSTRAINT angebot_art_status CHECK (
        intern = (status IN ('LAEUFT', 'ABGESCHLOSSEN')));
