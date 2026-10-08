-- fb.crm — Die eigenen Angaben als Stammdatum (Plan #87, E1; Kriterium 1).
--
-- Genau ein Satz Angaben je Instanz: Der Primaerschluessel traegt den Vorgabewert 1 und einen
-- CHECK darauf, damit eine zweite Zeile gar nicht erst entstehen kann. Eine Tabelle ohne
-- Schluesselspalte gaebe dieselbe Zusage nicht; ein Singleton in der Anwendung waere eine Zusage
-- an der falschen Stelle.
--
-- Jede Fachspalte darf fehlen: Die Angaben werden nach und nach vervollstaendigt, und „nicht
-- angegeben" ist NULL und nie der Leerstring — dieselbe Regel wie E9 des Firma-Moduls. Die
-- Laengen sind dieselben, die die Bean Validation an der Schnittstelle zieht; ohne sie
-- antwortete die Anwendung auf eine zu lange Eingabe mit einem Datenbankfehler statt mit einer
-- Meldung am Feld. Die Zahlungsbedingungen sind ein Fliesstext und deshalb "text", wie der Text
-- eines Historieneintrags in V3.
--
-- Die eine Zeile wird hier gleich mit angelegt. Damit braucht der Leseweg keinen Zweig „noch
-- keine Zeile", und der GET einer frischen Instanz antwortet mit lauter leeren Feldern statt mit
-- 404.

CREATE TABLE eigene_angaben (
    id                  smallint    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    name                varchar(200),
    strasse             varchar(200),
    plz                 varchar(20),
    ort                 varchar(200),
    land                varchar(100),
    email               varchar(320),
    telefon             varchar(50),
    steuernummer        varchar(50),
    umsatzsteuer_id     varchar(50),
    bankverbindung      varchar(200),
    zahlungsbedingungen text,
    updated_at          timestamptz NOT NULL DEFAULT now()
);

INSERT INTO eigene_angaben DEFAULT VALUES;
