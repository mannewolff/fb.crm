-- fb.crm — Der Auftrag als eigenstaendiges Dokument (Plan #112; Kriterien 3, 4, 5, 15).
--
-- E1 — Der Auftrag bekommt ein eigenes Schema, weil er ein eigenes Modul bekommt: Kapitel 03 der
-- Spezifikation fuehrt Angebot, Auftrag und Rechnung als eigenstaendige Dokumente, und der Auftrag
-- hat einen eigenen Lebenszyklus, eine eigene Nummer und eine eigene Auswertung — das Angebot ist
-- nur seine Quelle. Die Richtung ist auftrag → angebot, vorgang: auftrag.angebot_id nennt die
-- Quelle, auftrag.vorgang_id die Klammer, und keine der beiden Tabellen weiss vom Auftrag.
-- angebot_id ist UNIQUE — zu einem Angebot gehoert hoechstens ein Auftrag (F9).
--
-- E6 — Der Nummernkreis ist eine eigene Zaehlertabelle je Jahr und keine Sequenz, aus demselben
-- Grund wie E6 in V6: Eine Sequenz ist nicht transaktional. Gezogen wird in der Transaktion des
-- Anlegens (INSERT … ON CONFLICT DO NOTHING, dann SELECT … FOR UPDATE); ein zurueckgerollter
-- Anlegeversuch gibt die Nummer damit wieder frei. Das Jahr ist das Kalenderjahr des ANLEGENS in
-- common.Geschaeftszone und nicht das des frei setzbaren auftrag_datum. Anders als beim Angebot sind
-- Luecken ausdruecklich erlaubt (Kriterium 3): Eine Loeschung gibt die Nummer nicht zurueck, und
-- nachgerueckt wird nicht — ein Auftrag ist kein steuerlicher Beleg. Eine gemeinsame Tabelle fuer
-- beide Belegarten gibt es nicht; eine Tabelle, die zwei Module besitzen, hat keinen Eigentuemer.
--
-- E10 — „Stunden je Personentag" steht an der Position und haengt am Abrechnungsmodus, nicht an der
-- Einheit: Eine Aufwandsposition in Stunden traegt den Faktor ebenso wie eine in Personentagen, denn
-- gegen beide wird spaeter Zeit gebucht. numeric(4,2) und keine ganze Zahl, weil 7,5 verbreitet ist.
-- Die zwei gegenlaeufigen Checks halten ihn genau bei AUFWAND und dort ueber null — ein Personentag
-- ohne Stunden waere keine Umrechnung.
--
-- E11 — Gerechnete Werte stehen in keiner Spalte, wie E5 in V6: Positionsbetrag und Auftragssumme
-- sind Funktionen ihrer Eingaben und werden von der Domaene gerechnet. Eine gespeicherte Addition
-- waere ein zweiter Wahrheitsort, der bei jeder Aenderung nachgepflegt werden muesste.
--
-- E21 — Der Leistungszeitraum ist ganz da oder gar nicht, und sein Ende liegt nicht vor seinem
-- Beginn (Kriterium 3). Beides steht hier als CHECK und zusaetzlich als Klassen-Constraint an den
-- Anfragen der Schnittstelle: Die Meldung am Feld braucht die Maske, den Riegel die
-- Datenintegritaet. Ein Datenbankfehler ohne Feldnamen ist fuer die Maske unbrauchbar, und eine
-- Pruefung, die nur in der Anwendung steht, ist keine Schranke.
--
-- Kriterium 15 — Geloescht wird echt und ohne Statusspur: Es gibt keinen Status „geloescht", und an
-- keinem Fremdschluessel steht ein ON DELETE. Eine Zeile verschwindet nur, wenn der Anwendungsfall
-- es ausdruecklich sagt — erst die Positionen, dann die Zeile, die sie traegt.

CREATE TABLE auftrag (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vorgang_id          bigint       NOT NULL REFERENCES vorgang (id),
    angebot_id          bigint       NOT NULL UNIQUE REFERENCES angebot (id),
    nummer              varchar(20)  NOT NULL UNIQUE,
    status              varchar(20)  NOT NULL,
    auftrag_datum       date         NOT NULL,
    kundenbestellnummer varchar(100),
    leistung_ab         date,
    leistung_bis        date,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT auftrag_status CHECK (status IN ('OFFEN', 'IN_ARBEIT', 'ABGESCHLOSSEN')),
    -- Beide oder keines, und das Ende nicht vor dem Beginn — der gleiche Tag zaehlt noch mit (E21).
    CONSTRAINT auftrag_leistungszeitraum CHECK (
        (leistung_ab IS NULL) = (leistung_bis IS NULL)
            AND (leistung_ab IS NULL OR leistung_bis >= leistung_ab))
);

-- Nachgeschlagen wird entlang des Vorgangs: die Auftragsliste in seiner Detailansicht
-- (Kriterium 9). Einen Index auf status gibt es bewusst nicht — drei Werte auf der Tabelle eines
-- Freiberuflers, und der Vorgangsfilter des Auftragsbestands liegt in der Anwendungsschicht.
CREATE INDEX auftrag_vorgang_idx ON auftrag (vorgang_id);

CREATE TABLE auftrag_position (
    id                     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auftrag_id             bigint        NOT NULL REFERENCES auftrag (id),
    position               smallint      NOT NULL,
    bezeichnung            varchar(300)  NOT NULL,
    abrechnungsmodus       varchar(20)   NOT NULL,
    menge                  numeric(12,2) NOT NULL,
    einheit                varchar(20)   NOT NULL,
    einzelpreis            numeric(12,2) NOT NULL,
    stunden_je_personentag numeric(4,2),
    -- Die Werte stehen Wort fuer Wort wie in V6__angebot.sql: Dieselben Aufzaehlungstypen aus
    -- common tragen beide Belegarten, und zwei Abschriften derselben Liste liefen auseinander.
    CONSTRAINT auftrag_position_abrechnungsmodus CHECK (
        abrechnungsmodus IN ('AUFWAND', 'FESTPREIS')),
    CONSTRAINT auftrag_position_einheit CHECK (einheit IN ('STUNDE', 'PERSONENTAG', 'PAUSCHAL')),
    -- Eine negative Menge oder ein negativer Einzelpreis ist keine unvollstaendige Angabe, sondern
    -- eine falsche.
    CONSTRAINT auftrag_position_menge CHECK (menge >= 0),
    CONSTRAINT auftrag_position_einzelpreis CHECK (einzelpreis >= 0),
    -- Die beiden gegenlaeufigen Checks nach dem Muster von V3 und V6: Jeder laesst den Modus der
    -- anderen Seite durch und prueft seine eigene Seite vollstaendig. Weil jeder die Gegenseite
    -- ausdruecklich benennt statt sie nur auszunehmen, faellt ein dritter Modus durch beide (E10).
    CONSTRAINT auftrag_position_aufwand CHECK (
        abrechnungsmodus = 'FESTPREIS'
            OR (abrechnungsmodus = 'AUFWAND'
                AND stunden_je_personentag IS NOT NULL
                AND stunden_je_personentag > 0)),
    CONSTRAINT auftrag_position_festpreis CHECK (
        abrechnungsmodus = 'AUFWAND'
            OR (abrechnungsmodus = 'FESTPREIS' AND stunden_je_personentag IS NULL)),
    -- Die Reihenfolge ist Teil des Bestands und nicht der Zufall der Einfuegereihenfolge; zwei
    -- Positionen auf demselben Platz gibt es nicht.
    CONSTRAINT auftrag_position_reihenfolge UNIQUE (auftrag_id, position)
);

CREATE TABLE auftrag_nummernkreis (
    jahr     int    NOT NULL PRIMARY KEY,
    naechste bigint NOT NULL
);
