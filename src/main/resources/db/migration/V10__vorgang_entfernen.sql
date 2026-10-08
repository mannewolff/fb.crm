-- fb.crm — Der Vorgang entfaellt, das Angebot geht direkt an eine Firma (Issue #126).
--
-- Der Umfang wurde am 2026-09-29 verkleinert: Ein Angebot gehoert zu einer Firma und optional zu
-- einem ihrer Ansprechpartner; die Klammer mit Historie, Anhaengen und Phase gibt es nicht mehr.
-- V3, V4 und V7 bleiben unveraendert, damit bestehende Datenbanken weiter migrierbar sind; diese
-- Migration baut ab, was sie angelegt haben.
--
-- Bestehende Angebote behalten ihren Kunden: Firma und Ansprechpartner kommen aus dem Vorgang, an
-- dem sie hingen. Erst danach wird firma_id Pflicht — vorher waere jede bestehende Zeile ein
-- Verstoss. Der Ansprechpartner bleibt optional wie am Vorgang.
--
-- Anhaenge im Objektspeicher bleiben als verwaiste Objekte liegen: Es gibt nur Entwicklungsdaten,
-- und ein Loeschlauf gegen MinIO waere Code, der genau einmal laeuft.

ALTER TABLE angebot
    ADD COLUMN firma_id           bigint REFERENCES firma (id),
    ADD COLUMN ansprechpartner_id bigint REFERENCES ansprechpartner (id);

UPDATE angebot a
SET firma_id           = v.firma_id,
    ansprechpartner_id = v.ansprechpartner_id
FROM vorgang v
WHERE v.id = a.vorgang_id;

ALTER TABLE angebot ALTER COLUMN firma_id SET NOT NULL;

-- Die Angebotsliste einer Firma ist der einzige Listenweg (JpaAngebotRepository.findByFirma).
CREATE INDEX angebot_firma_idx ON angebot (firma_id);

-- Mit der Spalte fallen ihr Index und ihr Fremdschluessel.
ALTER TABLE angebot DROP COLUMN vorgang_id;

-- Die Reihenfolge folgt den Fremdschluesseln: erst die Eintraege, dann der Vorgang, der sie traegt.
-- Der Nummernkreis haengt an nichts. Mit der Tabelle vorgang fallen auch die Felder aus V7.
DROP TABLE vorgang_eintrag;
DROP TABLE vorgang;
DROP TABLE vorgang_nummernkreis;
