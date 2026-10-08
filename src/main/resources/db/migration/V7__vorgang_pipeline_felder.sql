-- fb.crm — Die zwei Pipeline-Felder am Vorgang (Plan #87; Kriterium 21).
--
-- Beide Angaben duerfen fehlen: Ein Vorgang wird angelegt, lange bevor sich abschaetzen laesst, wie
-- er ausgeht. „Nicht eingeschaetzt" ist NULL — dieselbe Regel wie in V5, und die Pipeline rechnet
-- den Ersatzwert 50 % erst beim Auswerten, damit die Zeile weiter als „nicht eingeschaetzt"
-- erkennbar bleibt.
--
-- Die Abschlusswahrscheinlichkeit steht in Zehnerschritten (Kriterium 21) und traegt den CHECK
-- dazu: Die Oberflaeche bietet nur die elf Werte an, aber die Oberflaeche ist keine Schranke. Der
-- Wertebereich gehoert dorthin, wo er nicht umgangen werden kann — dieselbe Bean Validation steht
-- zusaetzlich an der Schnittstelle, damit eine falsche Eingabe als Meldung am Feld und nicht als
-- Datenbankfehler zurueckkommt.
--
-- Der erwartete Entscheidungszeitpunkt ist ein Tag und kein Zeitpunkt: Gemeint ist „wann faellt die
-- Entscheidung", nicht „zu welcher Minute" — "date" statt "timestamptz" schliesst die Frage nach
-- der Zeitzone gar nicht erst auf.
--
-- Die Phase bekommt weiterhin keine Spalte. Sie wird abgeleitet (E4), jetzt aus dem Belegstand der
-- Angebote: Ein Vorgang mit mindestens einem festgeschriebenen Angebot steht in „Angebot".

ALTER TABLE vorgang
    ADD COLUMN abschlusswahrscheinlichkeit smallint,
    ADD COLUMN entscheidung_erwartet_am    date,
    ADD CONSTRAINT vorgang_abschlusswahrscheinlichkeit_zehnerschritt
        CHECK (abschlusswahrscheinlichkeit IS NULL
            OR (abschlusswahrscheinlichkeit BETWEEN 0 AND 100
                AND abschlusswahrscheinlichkeit % 10 = 0));
