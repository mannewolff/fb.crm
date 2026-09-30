-- fb.crm — Die Rechnung und ihre Positionen (Plan #169, E3; fachliche Quelle #160).
--
-- Die Rechnung gehoert zu genau einem Angebot und rechnet dessen Positionen ganz oder teilweise ab.
-- Der Fremdschluessel traegt kein ON DELETE, wie jeder Fremdschluessel dieses Schemas: Geloescht
-- wird hier nichts hinter dem Ruecken des Anwenders. Ein Rechnungsentwurf ist die Ausnahme (#160,
-- Kriterium 12); er verschwindet, und weil er nie eine Nummer getragen hat, reisst er keine Luecke.
--
-- E5 (aus V6__angebot.sql) — Gerechnete Werte stehen in keiner Spalte. Was eine Position kostet,
-- ist Menge mal Einzelpreis; was die Rechnung kostet, ist die Addition der gerundeten Einzelwerte;
-- Steuer und Brutto entstehen aus dieser Summe. Alles das rechnet die Domaene. Eine gespeicherte
-- Summe waere ein zweiter Wahrheitsort, der bei jeder Aenderung nachgepflegt werden muesste.
--
-- Kopien statt Verweise (R8, #160 Kriterium 14) — Empfaenger und eigene Angaben stehen ab dem
-- Stellen als Text in dieser Zeile. Zieht die Firma um oder wechselt die Bankverbindung, zeigt die
-- gestellte Rechnung weiterhin, was der Kunde auf seinem Dokument gelesen hat. Die Laengen sind
-- dieselben wie in den Quelltabellen firma (V2) und eigene_angaben (V5, V16); die Kopie soll nicht
-- kuerzer sein duerfen als ihre Quelle. Einen Ansprechpartner fuehrt die Kopie nicht: Kriterium 12
-- verlangt die Firmenanschrift, nicht eine Person (siehe Belegempfaenger).
--
-- Die beiden gegenlaeufigen CHECKs folgen dem Muster aus V6__angebot.sql: Jeder nennt die
-- Gegenseite ausdruecklich, statt sie nur auszunehmen. Ein dritter Zustand faellt damit durch
-- beide und nicht nur durch rechnung_zustand.
--
-- Das Dokument fehlt bei GESTELLT bewusst in der Pflichtliste. Es entsteht erst unmittelbar nach
-- dem Festschreiben und wird im selben Zug nachgetragen (Plan #169, E7); zwischen Flush und
-- Nachtrag traegt die Zeile den Zustand GESTELLT ohne pdf_schluessel. Waere das Dokument Pflicht,
-- muesste die Nummer erst nach dem Erzeugen des PDF geschrieben werden — dann risse ein
-- fehlgeschlagener Druck eine Luecke in den Nummernkreis.

CREATE TABLE rechnung (
    id                         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    angebot_id                 bigint       NOT NULL REFERENCES angebot (id),
    zustand                    varchar(20)  NOT NULL,
    rechnung_datum             date         NOT NULL,
    leistungszeitraum          varchar(100),
    -- Ab dem Stellen gefuellt.
    nummer                     varchar(50)  UNIQUE,
    steuersatz                 numeric(5,2),
    zahlungsziel_tage          integer,
    gestellt_am                timestamptz,
    pdf_schluessel             varchar(300),
    empfaenger_firma           varchar(200),
    empfaenger_strasse         varchar(200),
    empfaenger_plz             varchar(20),
    empfaenger_ort             varchar(200),
    empfaenger_land            varchar(100),
    absender_name              varchar(200),
    absender_berufsbezeichnung varchar(200),
    absender_strasse           varchar(200),
    absender_plz               varchar(20),
    absender_ort               varchar(200),
    absender_land              varchar(100),
    absender_email             varchar(320),
    absender_telefon           varchar(50),
    absender_steuernummer      varchar(50),
    absender_umsatzsteuer_id   varchar(50),
    absender_bankverbindung    varchar(200),
    absender_webadresse        varchar(200),
    created_at                 timestamptz  NOT NULL DEFAULT now(),
    updated_at                 timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT rechnung_zustand CHECK (zustand IN ('ENTWURF', 'GESTELLT')),
    CONSTRAINT rechnung_entwurf CHECK (
        zustand = 'GESTELLT'
            OR (zustand = 'ENTWURF'
                AND nummer IS NULL
                AND pdf_schluessel IS NULL
                AND gestellt_am IS NULL)),
    CONSTRAINT rechnung_gestellt CHECK (
        zustand = 'ENTWURF'
            OR (zustand = 'GESTELLT'
                AND nummer IS NOT NULL
                AND steuersatz IS NOT NULL
                AND zahlungsziel_tage IS NOT NULL
                AND gestellt_am IS NOT NULL
                AND empfaenger_firma IS NOT NULL
                AND absender_name IS NOT NULL))
);

-- Nachgeschlagen wird entlang des Angebots: die Rechnungen in seiner Ansicht und der
-- Abrechnungsstand seiner Positionen (#160, Kriterium 26).
CREATE INDEX rechnung_angebot_idx ON rechnung (angebot_id);

CREATE TABLE rechnung_position (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rechnung_id         bigint        NOT NULL REFERENCES rechnung (id),
    angebot_position_id bigint        NOT NULL REFERENCES angebot_position (id),
    position            smallint      NOT NULL,
    bezeichnung         varchar(300)  NOT NULL,
    einheit             varchar(20)   NOT NULL,
    menge               numeric(12,2) NOT NULL,
    einzelpreis         numeric(12,2) NOT NULL,
    CONSTRAINT rechnung_position_einheit CHECK (einheit IN ('STUNDE', 'PERSONENTAG', 'PAUSCHAL')),
    -- Groesser 0 und nicht nur "nicht negativ": Eine Position ueber nichts ist keine Position.
    -- Wer eine Leistung nicht abrechnen will, laesst sie weg (Plan #169, E5).
    CONSTRAINT rechnung_position_menge CHECK (menge > 0),
    CONSTRAINT rechnung_position_einzelpreis CHECK (einzelpreis >= 0),
    -- Die Reihenfolge ist Teil des Bestands und nicht der Zufall der Einfuegereihenfolge (E24).
    -- Aufgeschoben aus demselben Grund wie angebot_position_reihenfolge seit V15: Faellt eine
    -- Position weg, ruecken die folgenden auf, und der Zwischenstand traegt zwangslaeufig zweimal
    -- denselben Platz. Aufgeschoben prueft die Datenbank erst beim Commit — der Zwischenstand darf
    -- sich widersprechen, das Ergebnis nicht.
    CONSTRAINT rechnung_position_reihenfolge UNIQUE (rechnung_id, position)
        DEFERRABLE INITIALLY DEFERRED,
    -- Je Rechnung hoechstens eine Zeile je Angebotsposition: Die Angebotsposition ist die Kennung
    -- der Rechnungsposition (siehe Rechnungsposition), und zwei Zeilen zu derselben waeren zwei
    -- Antworten auf die Frage, wie viel davon in dieser Rechnung steht.
    CONSTRAINT rechnung_position_je_angebotsposition UNIQUE (rechnung_id, angebot_position_id)
);

-- Der Abrechnungsstand fragt von der Angebotsposition aus: was steht zu ihr in Rechnungen (#160,
-- Kriterien 6 und 26).
CREATE INDEX rechnung_position_angebot_position_idx ON rechnung_position (angebot_position_id);
