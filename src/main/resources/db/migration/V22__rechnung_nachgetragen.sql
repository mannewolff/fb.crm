-- fb.crm — Die nachgetragene Rechnung und die schreibweiseunabhaengige Rechnungsnummer (Plan #259;
-- fachliche Quelle #254).
--
-- E1 — Eine eigene Tabelle und nicht die bestehende rechnung erweitert. Eine nachgetragene Rechnung
-- hat fb.crm nicht geschrieben: Es kennt weder ihre Positionen noch ihr Angebot. Von den Spalten
-- der Rechnung (V18, V21) teilt sie nur nummer, rechnung_datum, zustand und pdf_schluessel. Die
-- Erweiterung haette die CHECKs der gestellten Rechnung und deren Pflichtfelder bedingt gemacht —
-- die Festschreibung der von fb.crm geschriebenen Rechnungen soll aber unveraendert gelten.
--
-- Der Fremdschluessel auf firma traegt kein ON DELETE, wie jeder Fremdschluessel dieses Schemas:
-- Geloescht wird hier nichts hinter dem Ruecken des Anwenders. Die Rechnung verweist auf die Firma
-- und kopiert sie nicht; eine spaetere Umbenennung zeigt sich auch bei ihr (#254, Kriterium 11).
--
-- E5 — Netto und Brutto stehen als Spalten, anders als bei der Rechnung (E5 aus V6__angebot.sql).
-- Sie sind hier keine gerechneten Werte, sondern Eingaben: fb.crm rechnet sie nicht nach und
-- leitet keinen aus dem anderen ab (#254, Kriterium 3). Einen Steuersatz gibt es darum nicht.
--
-- E6 — Die Zustaende sind GESTELLT, BEZAHLT und ABGESCHRIEBEN; einen Entwurf gibt es bei ihr nicht
-- (#254, Kriterium 8). Ebenso fehlen gestellt_am, Zahlungsziel und die beiden Belegkopien.
--
-- E7 — Die Datumsgrenze „1. Januar des laufenden Jahres bis heute" steht in keinem CHECK. Ein CHECK
-- auf die Gegenwart machte dieselbe Zeile im naechsten Jahr ungueltig; die Grenze prueft der
-- Anwendungsfall mit der injizierten Uhr.
--
-- E9 — Die Betragsregeln stehen als CHECK neben der Pruefung in Schnittstelle und Domaene: Netto
-- ist mindestens 0, Brutto nicht kleiner als Netto. Damit ist Brutto ebenfalls nicht negativ.
--
-- E26 — Keine Spalte datei_name. Der Download heisst Rechnung-<nummer>.pdf wie bei der gestellten
-- Rechnung; ein gespeicherter Originalname haette keinen Ort, an dem er erscheint. pdf_schluessel
-- ist optional, weil das Original optional ist (#254, Kriterium 2).
--
-- E3 — Die Nummer ist je Tabelle eindeutig ueber lower(nummer): „RE-1" und „re-1" sind dieselbe
-- Nummer (#254, Kriterium 4), wie bei account_email_key (V1). Fuer rechnung ersetzt derselbe Index
-- die UNIQUE-Zusage aus V18, die die Schreibweise unterscheidet; deren Name ist der von Postgres
-- vergebene und wurde am laufenden Schema geprueft. Ein Entwurf ohne Nummer bleibt erlaubt: NULL
-- ist auch in einem eindeutigen Index von jedem anderen Wert verschieden. Ueber beide Tabellen
-- hinweg kann die Datenbank keine Zusage geben; diese Frage beantwortet die Anwendung an einer
-- Stelle.

CREATE TABLE rechnung_nachgetragen (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    firma_id       bigint        NOT NULL REFERENCES firma (id),
    nummer         varchar(50)   NOT NULL,
    rechnung_datum date          NOT NULL,
    netto          numeric(12,2) NOT NULL,
    brutto         numeric(12,2) NOT NULL,
    zustand        varchar(20)   NOT NULL,
    pdf_schluessel varchar(300),
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT rechnung_nachgetragen_zustand
        CHECK (zustand IN ('GESTELLT', 'BEZAHLT', 'ABGESCHRIEBEN')),
    CONSTRAINT rechnung_nachgetragen_netto CHECK (netto >= 0),
    CONSTRAINT rechnung_nachgetragen_brutto CHECK (brutto >= netto)
);

CREATE UNIQUE INDEX rechnung_nachgetragen_nummer_lower_key
    ON rechnung_nachgetragen (lower(nummer));

-- Nachgeschlagen wird auch entlang der Firma; ohne Index pruefte jedes Loeschen einer Firma die
-- ganze Tabelle auf Verweise.
CREATE INDEX rechnung_nachgetragen_firma_idx ON rechnung_nachgetragen (firma_id);

ALTER TABLE rechnung DROP CONSTRAINT rechnung_nummer_key;

CREATE UNIQUE INDEX rechnung_nummer_lower_key ON rechnung (lower(nummer));
