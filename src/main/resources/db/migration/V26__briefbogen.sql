-- fb.crm — Der Briefbogen: das eigene Briefpapier, die beiden Schalter und die sechs Raender
-- (Plan #363, E1, E2, E8 und E15; fachliche Quelle #361).
--
-- Genau eine Zeile je Instanz, nach dem Muster von V14__rechnung_einstellungen.sql: Der
-- Primaerschluessel traegt den Vorgabewert 1 und einen CHECK darauf, damit eine zweite Zeile gar
-- nicht erst entstehen kann. Eine eigene Tabelle und keine Spalten an rechnung_einstellungen: Die
-- Rechnungseinstellungen werden beim Stellen in die Rechnung festgeschrieben, der Briefbogen wirkt
-- nur im Augenblick des Drucks (E2).
--
-- Die vier Spalten des Blattes (Schluessel im Objektspeicher, Dateiname, Groesse, Seitenzahl) sind
-- NULL, solange kein Briefpapier hinterlegt ist, und stehen nur gemeinsam da — ein halbes Blatt
-- gibt es nicht. Das Briefpapier hat eine Seite fuer Seite 1 und optional eine fuer die
-- Folgeseiten.
--
-- Die Raender stehen in ganzen Zehntel-Millimetern (E8): 98,5 mm aus Kriterium 15 braucht genau
-- eine Nachkommastelle, und der Satz rechnet ganzzahlig. Die Vorgaben sind die aus Kriterium 15,
-- beide Schalter stehen auf „an". Die eine Zeile wird hier gleich mit angelegt; ersetzt oder
-- entfernt der Betreiber sein Briefpapier, bleiben Schalter und Raender stehen (Kriterium 16,
-- E15) — eine zweite Schreibweise fuer „Vorgabe" gibt es darum nicht.

CREATE TABLE briefbogen (
    id                smallint      PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    pdf_schluessel    varchar(255),
    dateiname         varchar(255),
    groesse           bigint,
    seiten            smallint      CHECK (seiten IN (1, 2)),
    absenderzeile     boolean       NOT NULL DEFAULT true,
    steuer_bank_zeile boolean       NOT NULL DEFAULT true,
    rand_links        integer       NOT NULL DEFAULT 250 CHECK (rand_links >= 0),
    rand_rechts       integer       NOT NULL DEFAULT 200 CHECK (rand_rechts >= 0),
    inhalt_oben       integer       NOT NULL DEFAULT 985 CHECK (inhalt_oben >= 0),
    rand_unten        integer       NOT NULL DEFAULT 200 CHECK (rand_unten >= 0),
    folge_oben        integer       NOT NULL DEFAULT 200 CHECK (folge_oben >= 0),
    folge_unten       integer       NOT NULL DEFAULT 200 CHECK (folge_unten >= 0),
    updated_at        timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT briefbogen_blatt_ganz_oder_gar_nicht CHECK (
        (pdf_schluessel IS NULL AND dateiname IS NULL AND groesse IS NULL AND seiten IS NULL)
        OR (pdf_schluessel IS NOT NULL AND dateiname IS NOT NULL AND groesse IS NOT NULL
            AND seiten IS NOT NULL)
    )
);

INSERT INTO briefbogen DEFAULT VALUES;
