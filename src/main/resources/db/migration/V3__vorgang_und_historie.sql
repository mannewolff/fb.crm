-- fb.crm — Vorgang und durchgehende Historie, der erste Geschaeftsprozess (Plan #56).
--
-- E3 — Die Vorgangsnummer kommt aus der Zaehlertabelle "vorgang_nummernkreis" und nicht aus einer
-- Sequenz: Eine Sequenz ist nicht transaktional, jeder zurueckgerollte Versuch risse eine Luecke,
-- waehrend die Zusage "die naechste freie Nummer ab 1" lautet. Gezogen wird per
-- SELECT … FOR UPDATE in der Transaktion des Aufrufers; UNIQUE auf vorgang.nummer haelt dagegen.
--
-- E4 — Die Phase steht bewusst in keiner Spalte. Solange keine Dokumente am Vorgang haengen, ist
-- sie immer "Anbahnung" und wird aus dem Bestand abgeleitet (Vorgang.phase()). Eine Spalte, die
-- kein Code schreibt, laedt dazu ein, sie von Hand zu pflegen.
--
-- E5 — Der Abschlussstand ist ein Schalter "abgeschlossen boolean", nach dem Muster von "aktiv" an
-- Firma und Ansprechpartner. Ein Zeitstempel waere der Anfang einer Historie des Abschlusses, die
-- niemand angefordert hat.
--
-- E6 — Kommentare und Anhaenge liegen in *einer* Tabelle mit der Unterscheidungsspalte "art". Die
-- Ansicht liest beide Arten als eine nach Zeitpunkt sortierte Folge; zwei Tabellen verlangten eine
-- Mischsortierung in der Anwendung.
--
-- E12 — Der hochgeladene Inhaltstyp wird nicht gespeichert. Ausgeliefert wird ohnehin immer
-- application/octet-stream; eine gespeicherte Angabe haette keinen Leser und waere eine Einladung,
-- sie spaeter doch auszuliefern.
--
-- Geloescht wird nichts — deshalb steht an keinem Fremdschluessel ein ON DELETE.

CREATE TABLE vorgang (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nummer             bigint       NOT NULL UNIQUE,
    titel              varchar(300) NOT NULL,
    firma_id           bigint       NOT NULL REFERENCES firma (id),
    ansprechpartner_id bigint REFERENCES ansprechpartner (id),
    abgeschlossen      boolean      NOT NULL DEFAULT false,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT vorgang_titel_nicht_leer CHECK (btrim(titel) <> '')
);

-- Die Suche fragt nach einem Teil des Titels ohne Ruecksicht auf Gross- und Kleinschreibung; der
-- Index traegt die Gleichheitsfaelle. Fuer den Teilstring selbst kann kein B-Tree etwas tun, und
-- pg_trgm waere bei der erwarteten Datenmenge Aufwand ohne Wirkung (wie E13 in Plan #37).
CREATE INDEX vorgang_titel_idx ON vorgang (lower(titel));

-- Nachgeschlagen wird entlang der Firma: die Vorgangsliste in der Detailansicht der Firma.
CREATE INDEX vorgang_firma_idx ON vorgang (firma_id);

CREATE TABLE vorgang_eintrag (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vorgang_id        bigint      NOT NULL REFERENCES vorgang (id),
    art               varchar(20) NOT NULL,
    text              text,
    geschehen_am      timestamptz NOT NULL,
    herkunft          varchar(20) NOT NULL,
    datei_name        varchar(255),
    datei_groesse     bigint,
    objekt_schluessel varchar(300),
    created_at        timestamptz NOT NULL DEFAULT now(),
    geaendert_am      timestamptz,
    -- Die beiden Checks sichern die Arten aus E6 und zugleich den Wertebereich von "art": Eine
    -- dritte Art faellt durch beide, weil jeder Check die jeweils andere Art ausdruecklich
    -- benennt statt sie nur auszunehmen.
    CONSTRAINT vorgang_eintrag_kommentar CHECK (
        art = 'ANHANG'
            OR (art = 'KOMMENTAR'
                AND text IS NOT NULL AND btrim(text) <> ''
                AND datei_name IS NULL
                AND datei_groesse IS NULL
                AND objekt_schluessel IS NULL)),
    CONSTRAINT vorgang_eintrag_anhang CHECK (
        art = 'KOMMENTAR'
            OR (art = 'ANHANG'
                AND datei_name IS NOT NULL
                AND datei_groesse IS NOT NULL
                AND objekt_schluessel IS NOT NULL))
);

-- Die Historie wird immer je Vorgang und immer in derselben Reihenfolge gelesen: juengstes
-- Geschehen oben, bei gleichem Zeitpunkt der zuletzt angelegte Eintrag oben.
CREATE INDEX vorgang_eintrag_verlauf_idx
    ON vorgang_eintrag (vorgang_id, geschehen_am DESC, id DESC);

-- Genau eine Zeile: Der Check auf die Kennung macht eine zweite unmoeglich, damit die Sperre aus
-- E3 nicht ins Leere greifen kann.
CREATE TABLE vorgang_nummernkreis (
    id       smallint NOT NULL PRIMARY KEY DEFAULT 1,
    naechste bigint   NOT NULL,
    CONSTRAINT vorgang_nummernkreis_eine_zeile CHECK (id = 1)
);

INSERT INTO vorgang_nummernkreis (id, naechste) VALUES (1, 1);
