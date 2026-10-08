-- fb.crm — Erfasste Arbeitszeit auf einer Angebotsposition (Plan #194, A3, A4; fachliche
-- Quelle #193, Kriterium 1).
--
-- Ein Zeiteintrag ist ein Tag, eine Uhrzeit von und bis und die Angebotsposition, auf die gebucht
-- wurde. Mehr traegt die Zeile nicht: Welche Position buchbar ist, steht am Angebot
-- (abrechnungsmodus = 'AUFWAND' und einheit = 'STUNDE'), und eine Kopie dieser Merkmale waere ein
-- zweiter Wahrheitsort, der bei jeder Aenderung am Angebot nachgezogen werden muesste.
--
-- A3 — Die Dauer wird nicht gespeichert, sondern aus von und bis gerechnet. Dieselbe Ueberlegung
-- wie bei der offenen Menge der Rechnung (Positionsstand, Plan #169, E6): Eine gespeicherte Dauer
-- muesste bei jeder Korrektur von von oder bis mitwandern, und wo sie es einmal nicht tut, gibt es
-- zwei Antworten auf die Frage, wie lange gearbeitet wurde.
--
-- Am Fremdschluessel steht kein ON DELETE, wie an jedem Fremdschluessel dieses Schemas. Eine
-- Angebotsposition mit erfasster Arbeitszeit verschwindet nicht hinter dem Ruecken des Anwenders:
-- Das Entfernen am Angebot weist der Anwendungsfall mit einer Meldung ab (Plan #194, A2, E3), und
-- der Fremdschluessel ist die letzte Sicherung dahinter. TRUNCATE … CASCADE der
-- Integrationstests erreicht die Zeilen trotzdem.
--
-- A4 — Die Datenbank prueft, was zu einem Eintrag allein gehoert: bis > von und das Raster der
-- Viertelstunde. Beides kommt beim Nutzer als Meldung am Feld an, die der Anwendungsfall erzeugt
-- (A19); die CHECKs sind die letzte Sicherung fuer jeden Weg, der nicht durch den Anwendungsfall
-- fuehrt. Die Ueberschneidung zweier Eintraege prueft dagegen ausdruecklich NICHT die Datenbank:
-- Bei einer Person an einem Rechner gibt es keinen gleichzeitigen zweiten Schreiber (CLAUDE.md,
-- „Betriebsform"), und nur der Anwendungsfall kann die Meldung mit dem kollidierenden Eintrag
-- liefern, die Kriterium 4 verlangt — eine Ausschlussbedingung lieferte einen Fehler ohne diesen
-- Bezug.
--
-- EXTRACT(SECOND FROM …) liefert in PostgreSQL die Sekunde samt ihrem Bruchteil als numeric; der
-- Vergleich mit 0 weist damit auch 09:00:00.5 ab, ohne dass die Mikrosekunden eine eigene
-- Bedingung brauchen.
--
-- Eine Obergrenze fuer die Uhrzeit steht hier nicht (E6): bis > von und der Wertebereich von time
-- lassen als spaetestes Ende 23:45 zu, und mehr waere eine Regel ohne eigene Aussage.

CREATE TABLE arbeitszeit (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    angebot_position_id bigint      NOT NULL REFERENCES angebot_position (id),
    tag                 date        NOT NULL,
    von                 time        NOT NULL,
    bis                 time        NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT arbeitszeit_zeitraum CHECK (bis > von),
    CONSTRAINT arbeitszeit_raster CHECK (
        EXTRACT(MINUTE FROM von) IN (0, 15, 30, 45)
            AND EXTRACT(SECOND FROM von) = 0
            AND EXTRACT(MINUTE FROM bis) IN (0, 15, 30, 45)
            AND EXTRACT(SECOND FROM bis) = 0)
);

-- Nachgeschlagen wird je Monat: die Monatsliste der Ansicht und die Ueberschneidungspruefung eines
-- Tages (#193, Kriterien 4 und 5).
CREATE INDEX arbeitszeit_tag_idx ON arbeitszeit (tag);

-- Und je Position: die angefallenen Stunden am Angebot und der Vorschlag in der Rechnung
-- (#193, Kriterien 7 und 9).
CREATE INDEX arbeitszeit_angebot_position_idx ON arbeitszeit (angebot_position_id);
