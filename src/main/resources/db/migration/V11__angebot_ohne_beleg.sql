-- fb.crm — Das Angebot ohne Beleg: Mappe mit Status statt festgeschriebenem Dokument (Issue #133).
--
-- Das Angebot entsteht ausserhalb des Tools; hier bleibt die Mappe dazu: Firma, Ansprechpartner,
-- Datum, ein Text, Positionen und einer von fuenf Status (Plan #131, fachliche Quelle #127).
-- Nummer, Dokument, Versand, Reaktion, Gueltigkeit und Zahlungsbedingungen fallen weg. V6 bis V10
-- bleiben unveraendert, damit bestehende Datenbanken weiter migrierbar sind.
--
-- Bestehende Angebote gehen nicht verloren (Kriterium 10): Sie bekommen den Status, der ihrem
-- bisherigen Zustand entspricht, und ihre Zahlungsbedingungen stehen danach am Ende ihres Texts.
-- Ein leerer Teil verschluckt dabei nichts — concat_ws uebergeht NULL, und nullif(btrim(..)) macht
-- aus einem Text aus Leerzeichen ein NULL.

ALTER TABLE angebot ADD COLUMN status varchar(20);

UPDATE angebot
SET status = CASE zustand
                 WHEN 'ENTWURF' THEN 'ANGELEGT'
                 WHEN 'ANGENOMMEN' THEN 'BESTELLT'
                 ELSE 'ABGEGEBEN'
             END;

UPDATE angebot
SET leistungsbeschreibung = concat_ws(E'\n\n',
                                      nullif(btrim(leistungsbeschreibung), ''),
                                      nullif(btrim(zahlungsbedingungen), ''))
WHERE nullif(btrim(zahlungsbedingungen), '') IS NOT NULL;

ALTER TABLE angebot
    ALTER COLUMN status SET NOT NULL,
    ADD CONSTRAINT angebot_status CHECK (
        status IN ('ANGELEGT', 'ABGEGEBEN', 'BESTELLT', 'ERLEDIGT', 'ABGERECHNET'));

-- Die Checks des Belegs zuerst, ausdruecklich: Sie nennen die Spalten, die gleich fallen.
ALTER TABLE angebot
    DROP CONSTRAINT angebot_zustand,
    DROP CONSTRAINT angebot_gueltigkeit,
    DROP CONSTRAINT angebot_entwurf,
    DROP CONSTRAINT angebot_festgeschrieben;

DROP INDEX angebot_zustand_gueltigkeit_idx;

ALTER TABLE angebot
    DROP COLUMN nummer,
    DROP COLUMN zustand,
    DROP COLUMN gueltig_bis,
    DROP COLUMN zahlungsbedingungen,
    DROP COLUMN versendet_am,
    DROP COLUMN reaktion_am,
    DROP COLUMN pdf_schluessel,
    DROP COLUMN empfaenger_firma,
    DROP COLUMN empfaenger_strasse,
    DROP COLUMN empfaenger_plz,
    DROP COLUMN empfaenger_ort,
    DROP COLUMN empfaenger_land,
    DROP COLUMN empfaenger_ansprechpartner,
    DROP COLUMN absender_name,
    DROP COLUMN absender_strasse,
    DROP COLUMN absender_plz,
    DROP COLUMN absender_ort,
    DROP COLUMN absender_land,
    DROP COLUMN absender_email,
    DROP COLUMN absender_telefon,
    DROP COLUMN absender_steuernummer,
    DROP COLUMN absender_umsatzsteuer_id,
    DROP COLUMN absender_bankverbindung;

-- Die Rechnung bekommt ihren eigenen Nummernkreis; der des Angebots hat keine Aufgabe mehr.
DROP TABLE angebot_nummernkreis;
