-- fb.crm — Firma und Ansprechpartner als erster fachlicher Stammsatz (Plan #37).
--
-- Beide Tabellen tragen den Stilllegungsstand als "aktiv boolean" und nicht als Zeitstempel: Der
-- Zeitpunkt waere der Anfang einer Historie, und Historie ist ausdruecklich Nicht-Ziel (E3).
-- Geloescht wird nichts — deshalb steht am Fremdschluessel bewusst kein ON DELETE CASCADE.

CREATE TABLE firma (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            varchar(200) NOT NULL,
    strasse         varchar(200),
    plz             varchar(20),
    ort             varchar(200),
    land            varchar(100),
    steuernummer    varchar(50),
    umsatzsteuer_id varchar(50),
    aktiv           boolean      NOT NULL DEFAULT true,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT firma_name_nicht_leer CHECK (btrim(name) <> '')
);

-- Der Index traegt die Sortierung der Uebersicht (ORDER BY lower(name), id). Fuer die Suche im
-- Namen gibt es bewusst keinen: Sie fragt nach einem Teilstring, den kein B-Tree bedienen kann,
-- und eine Erweiterung wie pg_trgm waere bei der erwarteten Datenmenge Aufwand ohne Wirkung (E13).
-- Kein eindeutiger Index auf dem Namen: Zwei Firmen duerfen gleich heissen.
CREATE INDEX firma_name_idx ON firma (lower(name));

CREATE TABLE ansprechpartner (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    firma_id         bigint       NOT NULL REFERENCES firma (id),
    vorname          varchar(200),
    nachname         varchar(200) NOT NULL,
    rolle            varchar(200),
    email            varchar(320),
    telefon_festnetz varchar(50),
    telefon_mobil    varchar(50),
    aktiv            boolean      NOT NULL DEFAULT true,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ansprechpartner_nachname_nicht_leer CHECK (btrim(nachname) <> '')
);

-- Nachgeschlagen wird immer entlang der Firma: die Liste einer Firma und die Zahl ihrer aktiven
-- Ansprechpartner.
CREATE INDEX ansprechpartner_firma_idx ON ansprechpartner (firma_id);
