-- fb.crm — Der Nummernkreis der Rechnung, je Zaehlerjahr (Plan #169, E4; #160, Kriterien 15 bis 17).
--
-- Eine Zaehlertabelle und keine Sequenz, aus demselben Grund wie E6 in V6__angebot.sql: Eine
-- Sequenz ist nicht transaktional, und eine abgewiesene oder zurueckgerollte Rechnung risse eine
-- Luecke, waehrend der Kreis lueckenlos zusagt. Gezogen wird, indem der Adapter die Jahreszeile
-- zuerst per INSERT … ON CONFLICT (jahr) DO NOTHING anlegt und sie danach mit SELECT … FOR UPDATE
-- sperrt. Ein FOR UPDATE allein sperrt eine fehlende Zeile nicht: Zwei gleichzeitige erste Zuege
-- eines Jahres laesen beide „nicht da", und der zweite scheiterte am Schluessel.
--
-- Der Schluessel ist das Zaehlerjahr, nicht das Kalenderjahr. Traegt das Nummernmuster einen
-- Jahres-Platzhalter, zaehlt jedes Jahr fuer sich; traegt es keinen, laeuft ein einziger Kreis
-- durch, und der steht unter der 0 — ein Jahr 0 gibt es sonst nicht, also ist der Schluessel
-- eindeutig. Gerechnet wird das Zaehlerjahr in der Anwendung an genau einer Stelle
-- (Nummernmuster#zaehlerjahr).
--
-- Der bisherige Wert zieht um. rechnung_einstellungen.naechste_nummer (V14) fuehrte die naechste
-- laufende Nummer als eine Spalte ohne Jahr; sie wird hier zum Zaehler des Zaehlerjahrs, das zum
-- gespeicherten Muster gehoert, und die Spalte entfaellt danach. Das Jahr kommt aus
-- Europe/Berlin und nicht aus der Zeitzone der Datenbanksitzung: Ob eine Nummer ins alte oder ins
-- neue Jahr gehoert, entscheidet der Kalender des Freiberuflers (common.Geschaeftszone) — in der
-- ersten Stunde des 1. Januar unterscheiden sich beide Antworten um ein Jahr.

CREATE TABLE rechnung_nummernkreis (
    jahr            integer PRIMARY KEY,
    naechste_nummer integer NOT NULL CHECK (naechste_nummer >= 1)
);

INSERT INTO rechnung_nummernkreis (jahr, naechste_nummer)
SELECT CASE
           WHEN nummer_muster LIKE '%{JJJJ}%' OR nummer_muster LIKE '%{JJ}%'
               THEN EXTRACT(YEAR FROM (now() AT TIME ZONE 'Europe/Berlin'))::integer
           ELSE 0
       END,
       naechste_nummer
FROM rechnung_einstellungen;

ALTER TABLE rechnung_einstellungen DROP COLUMN naechste_nummer;
