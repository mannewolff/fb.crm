-- fb.crm — Die Einstellungen zur Rechnung als Stammdatum (Plan #161, E2; Kriterien 1 und 11).
--
-- Genau ein Satz Einstellungen je Instanz, nach dem Muster von V5__eigene_angaben.sql: Der
-- Primaerschluessel traegt den Vorgabewert 1 und einen CHECK darauf, damit eine zweite Zeile gar
-- nicht erst entstehen kann.
--
-- Vier typisierte Spalten und keine Schluessel-Wert-Tabelle: So tragen die Grenzen aus den
-- Kriterien 7 bis 9 ihre CHECKs, und die Vorbelegungen aus Kriterium 11 stehen an einer Stelle.
-- Anders als bei den eigenen Angaben darf hier keine Fachspalte fehlen — eine Rechnung ohne
-- Steuersatz oder ohne Zahlungsziel gibt es nicht, und fuer jeden Wert steht eine sinnvolle
-- Vorbelegung fest.
--
-- Die Laengen und Grenzen sind dieselben, die die Bean Validation an der Schnittstelle zieht; ohne
-- sie antwortete die Anwendung auf eine unmoegliche Eingabe mit einem Datenbankfehler statt mit
-- einer Meldung am Feld. Der Steuersatz ist numeric(5,2) und kein Gleitkommawert: Er geht in eine
-- Geldrechnung ein.
--
-- Eine Spalte fuer das Jahr, fuer das die naechste Nummer gilt, gibt es hier nicht — der
-- Jahreswechsel ist Nicht-Ziel von #159 und kommt mit #160 (Plan #161, E3).
--
-- Die eine Zeile wird hier gleich mit angelegt. Damit braucht der Leseweg keinen Zweig „noch keine
-- Zeile", und der GET einer frischen Instanz antwortet mit den Vorbelegungen statt mit 404.

CREATE TABLE rechnung_einstellungen (
    id                smallint      PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    nummer_muster     varchar(50)   NOT NULL DEFAULT '{NNNN}-{JJJJ}',
    naechste_nummer   integer       NOT NULL DEFAULT 1 CHECK (naechste_nummer >= 1),
    steuersatz        numeric(5,2)  NOT NULL DEFAULT 19.00
                                    CHECK (steuersatz >= 0 AND steuersatz <= 100),
    zahlungsziel_tage integer       NOT NULL DEFAULT 10 CHECK (zahlungsziel_tage >= 0),
    updated_at        timestamptz   NOT NULL DEFAULT now()
);

INSERT INTO rechnung_einstellungen DEFAULT VALUES;
