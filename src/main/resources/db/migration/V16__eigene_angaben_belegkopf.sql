-- fb.crm — Die eigenen Angaben fuer den Kopf der Rechnung (Plan #169; Issue #173).
--
-- Der Belegkopf der Rechnung traegt eine Berufsbezeichnung und eine Webadresse (#160, Kriterium
-- 29); beides gab es unter „Eigene Angaben" noch nicht. Die Laengen sind dieselben, die die Bean
-- Validation an der Schnittstelle zieht; ohne sie antwortete die Anwendung auf eine zu lange
-- Eingabe mit einem Datenbankfehler statt mit einer Meldung am Feld. Beide Angaben duerfen
-- fehlen, wie jede andere Fachspalte dieser Tabelle: Die Angaben werden nach und nach
-- vervollstaendigt, und „nicht angegeben" ist NULL und nie der Leerstring (E9).
--
-- Dafuer faellt das Freitextfeld „Zahlungsbedingungen" weg: Den Satz zum Zahlungsziel bildet die
-- Rechnung aus den Einstellungen (#160, Kriterien 22 und 30), und zwei Quellen fuer denselben
-- Satz waeren eine zu viel. **Ein dort eingetragener Text geht dabei verloren** — die Spalte wird
-- nicht umgehaengt und nicht gesichert. Das ist entschieden, nicht uebersehen (#160, Frage 12;
-- Kriterium 30).
ALTER TABLE eigene_angaben
    ADD COLUMN berufsbezeichnung varchar(200),
    ADD COLUMN webadresse        varchar(200),
    DROP COLUMN zahlungsbedingungen;
