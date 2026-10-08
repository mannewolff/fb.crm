-- fb.crm — Das Angebot als eigenstaendiges Dokument (Plan #87, E1; Kriterien 3, 5, 11, 15, 17).
--
-- Das Angebot ist nach Kapitel 03 der Spezifikation ein Dokument mit eigenem Lebenszyklus und
-- deshalb eine eigene Wurzel mit eigenem Schema. Der Vorgang bleibt die Klammer: angebot.vorgang_id
-- verweist auf ihn, und geloescht wird auch hier nichts — an keinem Fremdschluessel steht ein
-- ON DELETE. Ein Entwurf ist die Ausnahme (Kriterium 7); er verschwindet, und weil er nie eine
-- Nummer getragen hat, reisst er keine Luecke.
--
-- E5 — Gerechnete Werte stehen in keiner Spalte. Was eine Position kostet, ist Menge mal
-- Einzelpreis, und was das Angebot kostet, ist die Addition der gerundeten Einzelwerte; beides
-- rechnet die Domaene. Eine gespeicherte Addition waere ein zweiter Wahrheitsort, der bei jeder
-- Aenderung einer Position nachgepflegt werden muesste.
--
-- E4 — „abgelaufen" ist kein Zustand. Es ist ein VERSENDETes Angebot mit verstrichener Gueltigkeit
-- und entsteht beim Lesen aus dem Vergleich mit dem heutigen Tag (Angebot.stand). Eine Spalte
-- dafuer muesste taeglich von einem Zeitgeber umgeschrieben werden, um wahr zu bleiben.
--
-- E27 — Die Datenbank traegt die Festschreibung, nicht die Vollstaendigkeit eines Entwurfs. Ein
-- Entwurf darf halbfertig sein: Gueltigkeit vor dem Angebotsdatum, eine Position mit leerer
-- Bezeichnung, kein Text. Was zum Versenden fehlt, sagt die Anwendung dem Menschen Feld fuer Feld
-- (Kriterium 12) — als CHECK gaebe es dafuer nur einen Datenbankfehler ohne Feldnamen. Deshalb
-- traegt bezeichnung bewusst keine btrim-Bedingung, und die Gueltigkeitsregel nimmt den ENTWURF aus.
--
-- E6 — Der Nummernkreis ist eine Zaehlertabelle je Jahr und keine Sequenz, aus demselben Grund wie
-- E3 in V3: Eine Sequenz ist nicht transaktional, und ein abgewiesener oder zurueckgerollter Versand
-- risse eine Luecke, waehrend Kriterium 11 einen lueckenlosen Kreis je Jahr zusagt. Gezogen wird per
-- SELECT … FOR UPDATE in der Transaktion des Aufrufers; UNIQUE auf angebot.nummer haelt dagegen.
-- Die Jahreszeile entsteht beim ersten Zug des Jahres (INSERT … ON CONFLICT DO NOTHING) und wird
-- hier nicht vorab angelegt — welches Jahr das erste ist, weiss die Migration nicht.

CREATE TABLE angebot (
    id                         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vorgang_id                 bigint      NOT NULL REFERENCES vorgang (id),
    nummer                     varchar(20) UNIQUE,
    zustand                    varchar(20) NOT NULL,
    angebot_datum              date        NOT NULL,
    gueltig_bis                date        NOT NULL,
    leistungsbeschreibung      text,
    zahlungsbedingungen        text,
    versendet_am               timestamptz,
    reaktion_am                timestamptz,
    pdf_schluessel             varchar(300),
    -- Die Kopie der Empfaengeranschrift (R8): Was beim Versenden galt, bleibt am Dokument stehen,
    -- auch wenn die Firma spaeter umzieht.
    empfaenger_firma           varchar(200),
    empfaenger_strasse         varchar(200),
    empfaenger_plz             varchar(20),
    empfaenger_ort             varchar(200),
    empfaenger_land            varchar(100),
    empfaenger_ansprechpartner varchar(200),
    -- Die Kopie der eigenen Angaben, aus demselben Grund. Die Laengen sind dieselben wie in
    -- V5__eigene_angaben.sql; die Kopie soll nicht kuerzer sein duerfen als ihre Quelle.
    absender_name              varchar(200),
    absender_strasse           varchar(200),
    absender_plz               varchar(20),
    absender_ort               varchar(200),
    absender_land              varchar(100),
    absender_email             varchar(320),
    absender_telefon           varchar(50),
    absender_steuernummer      varchar(50),
    absender_umsatzsteuer_id   varchar(50),
    absender_bankverbindung    varchar(200),
    created_at                 timestamptz NOT NULL DEFAULT now(),
    updated_at                 timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT angebot_zustand CHECK (
        zustand IN ('ENTWURF', 'VERSENDET', 'ANGENOMMEN', 'ABGELEHNT', 'ABGELOEST')),
    -- Der Entwurf traegt seine Gueltigkeit frei (E27); ab dem Versenden ist eine Gueltigkeit vor
    -- dem Angebotsdatum ein Widerspruch im festgeschriebenen Dokument.
    CONSTRAINT angebot_gueltigkeit CHECK (zustand = 'ENTWURF' OR gueltig_bis >= angebot_datum),
    -- Die beiden gegenlaeufigen Checks nach dem Muster von V3: Jeder laesst die Zustaende der
    -- anderen Seite durch und prueft seine eigene Seite vollstaendig. Weil jeder die Gegenseite
    -- ausdruecklich benennt statt sie nur auszunehmen, faellt ein sechster Zustand durch beide.
    CONSTRAINT angebot_entwurf CHECK (
        zustand IN ('VERSENDET', 'ANGENOMMEN', 'ABGELEHNT', 'ABGELOEST')
            OR (zustand = 'ENTWURF'
                AND nummer IS NULL
                AND versendet_am IS NULL
                AND pdf_schluessel IS NULL
                AND empfaenger_firma IS NULL
                AND empfaenger_strasse IS NULL
                AND empfaenger_plz IS NULL
                AND empfaenger_ort IS NULL
                AND empfaenger_land IS NULL
                AND empfaenger_ansprechpartner IS NULL
                AND absender_name IS NULL
                AND absender_strasse IS NULL
                AND absender_plz IS NULL
                AND absender_ort IS NULL
                AND absender_land IS NULL
                AND absender_email IS NULL
                AND absender_telefon IS NULL
                AND absender_steuernummer IS NULL
                AND absender_umsatzsteuer_id IS NULL
                AND absender_bankverbindung IS NULL)),
    CONSTRAINT angebot_festgeschrieben CHECK (
        zustand = 'ENTWURF'
            OR (zustand IN ('VERSENDET', 'ANGENOMMEN', 'ABGELEHNT', 'ABGELOEST')
                AND nummer IS NOT NULL
                AND versendet_am IS NOT NULL
                AND pdf_schluessel IS NOT NULL
                AND absender_name IS NOT NULL
                AND empfaenger_firma IS NOT NULL))
);

-- Nachgeschlagen wird entlang des Vorgangs: die Angebotsliste in seiner Detailansicht
-- (Kriterium 20).
CREATE INDEX angebot_vorgang_idx ON angebot (vorgang_id);

-- Die Auswertungen fragen nach offenen Angeboten und nach verstrichener Gueltigkeit — beides
-- zusammen in einer Spur, damit „welche Angebote sind noch offen und schon abgelaufen" ohne
-- Tabellenlauf beantwortbar bleibt.
CREATE INDEX angebot_zustand_gueltigkeit_idx ON angebot (zustand, gueltig_bis);

CREATE TABLE angebot_position (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    angebot_id       bigint        NOT NULL REFERENCES angebot (id),
    position         smallint      NOT NULL,
    bezeichnung      varchar(300)  NOT NULL,
    abrechnungsmodus varchar(20)   NOT NULL,
    menge            numeric(12,2) NOT NULL,
    einheit          varchar(20)   NOT NULL,
    einzelpreis      numeric(12,2) NOT NULL,
    CONSTRAINT angebot_position_abrechnungsmodus CHECK (
        abrechnungsmodus IN ('AUFWAND', 'FESTPREIS')),
    CONSTRAINT angebot_position_einheit CHECK (einheit IN ('STUNDE', 'PERSONENTAG', 'PAUSCHAL')),
    -- Eine negative Menge oder ein negativer Einzelpreis ist keine unvollstaendige Angabe, sondern
    -- eine falsche; deshalb steht sie hier und nicht erst in der Versandpruefung.
    CONSTRAINT angebot_position_menge CHECK (menge >= 0),
    CONSTRAINT angebot_position_einzelpreis CHECK (einzelpreis >= 0),
    -- Die Reihenfolge ist Teil des Bestands und nicht der Zufall der Einfuegereihenfolge; zwei
    -- Positionen auf demselben Platz gibt es nicht (E24).
    CONSTRAINT angebot_position_reihenfolge UNIQUE (angebot_id, position)
);

CREATE TABLE angebot_nummernkreis (
    jahr     int    NOT NULL PRIMARY KEY,
    naechste bigint NOT NULL
);
