-- fb.crm — Der Ausgang einer gestellten Rechnung: bezahlt oder abgeschrieben (Issue #253).
--
-- Bis hierher kannte die Rechnung zwei Zustaende, ENTWURF und GESTELLT (V18__rechnung.sql). Was
-- nach dem Stellen geschah, hielt die Anwendung nicht fest: Die Liste stand dauerhaft auf
-- „Gestellt", auch wenn der Kunde laengst gezahlt hatte oder nie zahlen wuerde (etwa bei
-- Insolvenz). BEZAHLT und ABGESCHRIEBEN kommen dazu und sagen genau das — und nichts darueber
-- hinaus: Ein Zahlungsdatum oder ein gezahlter Betrag steht in keiner Spalte, der Zahlungseingang
-- liegt laut CLAUDE.md ausserhalb des Umfangs.
--
-- Beide sind weiterhin gestellte Rechnungen. Nummer, Festschreibung, Dokument und die abgerechneten
-- Mengen bleiben unveraendert, und darum gelten die Pflichtfelder der gestellten Rechnung fuer alle
-- drei gleichermassen: Der CHECK rechnung_gestellt zaehlt nicht den einen Zustand GESTELLT auf,
-- sondern jeden ausser ENTWURF. Die Domaene stellt dieselbe Frage mit Rechnungszustand#istGestellt.
--
-- Die gegenlaeufige Form aus V18 bleibt erhalten: Jeder der beiden CHECKs nennt die Gegenseite
-- ausdruecklich, statt sie nur auszunehmen. Ein fuenfter Zustand faellt damit durch beide und nicht
-- nur durch rechnung_zustand.
--
-- Kein UPDATE auf vorhandene Zeilen: Die beiden alten Werte behalten ihre Bedeutung, und keine
-- bestehende Rechnung wechselt ihren Zustand durch diese Migration.

ALTER TABLE rechnung DROP CONSTRAINT rechnung_zustand;
ALTER TABLE rechnung DROP CONSTRAINT rechnung_entwurf;
ALTER TABLE rechnung DROP CONSTRAINT rechnung_gestellt;

ALTER TABLE rechnung
    ADD CONSTRAINT rechnung_zustand
        CHECK (zustand IN ('ENTWURF', 'GESTELLT', 'BEZAHLT', 'ABGESCHRIEBEN'));

ALTER TABLE rechnung
    ADD CONSTRAINT rechnung_entwurf CHECK (
        zustand IN ('GESTELLT', 'BEZAHLT', 'ABGESCHRIEBEN')
            OR (zustand = 'ENTWURF'
                AND nummer IS NULL
                AND pdf_schluessel IS NULL
                AND gestellt_am IS NULL));

-- Das Dokument fehlt weiter bewusst in der Pflichtliste, aus dem Grund, den V18 nennt: Es entsteht
-- erst unmittelbar nach dem Festschreiben und wird im selben Zug nachgetragen (Plan #169, E7).
ALTER TABLE rechnung
    ADD CONSTRAINT rechnung_gestellt CHECK (
        zustand = 'ENTWURF'
            OR (zustand IN ('GESTELLT', 'BEZAHLT', 'ABGESCHRIEBEN')
                AND nummer IS NOT NULL
                AND steuersatz IS NOT NULL
                AND zahlungsziel_tage IS NOT NULL
                AND gestellt_am IS NOT NULL
                AND empfaenger_firma IS NOT NULL
                AND absender_name IS NOT NULL));
