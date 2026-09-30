-- fb.crm — Kommentare am Angebot (Issue #143, Plan #141 E3, fachliche Quelle #140).
--
-- Der Kommentar ist ein eigenes Fachobjekt mit eigener Identitaet und keine Liste am Angebot (E2):
-- Das Angebot wird mit seinen Positionen als Ganzes geschrieben, und ein Kommentar darf bei
-- PUT /api/angebote/{id} nicht mitwandern. Deshalb eine eigene Tabelle mit eigenem Schluessel.
--
-- Am Fremdschluessel steht kein ON DELETE, wie an jedem Fremdschluessel dieses Schemas: Ein Angebot
-- wird nie geloescht (Issue #127), und ein stillschweigendes Mitloeschen waere eine Zusage, die
-- nichts braucht. TRUNCATE … CASCADE der Integrationstests erreicht die Zeilen trotzdem.
--
-- updated_at folgt der Konvention aller Fachtabellen (V2, V5, V6) und wird von keinem Weg
-- gelesen: Kriterium 9 der Quelle schliesst einen Hinweis „bearbeitet" aus, und keine Antwort
-- traegt das Feld. Die Spalte steht hier als Konvention, nicht als Vorrat.
--
-- Eine Verfasser-Spalte gibt es nicht: Die Fragen 5 und 7 der Quelle entscheiden gegen die Anzeige
-- eines Verfassers und gegen Rechte je Verfasser. Anders als updated_at haette sie weder einen
-- Leser noch eine Konvention hinter sich (CLAUDE-java.md §2.2).
--
-- Die beiden CHECKs sind die Zusagen, die allein die Datenbank halten kann. Gemessen wird der
-- gespeicherte Text: Der Anwendungsfall schneidet Leerraum am Rand ab (E7), und was danach
-- leer oder zu lang ist, kommt hier nicht durch — auch nicht auf einem Weg an der Anwendung vorbei.
-- Die Grenze an der Schnittstelle gilt fuer den eingereichten Text und steht in
-- AngebotKommentarRequest; sie ersetzt diesen CHECK nicht.
--
-- btrim bekommt seinen Zeichensatz ausdruecklich mitgegeben: Ohne zweites Argument schneidet es in
-- PostgreSQL nur das Leerzeichen ab, und ein Text aus Zeilenumbruechen und Tabulatoren kaeme durch,
-- obwohl Javas strip() ihn zu nichts macht. Aufgezaehlt sind Leerzeichen, Tabulator, Zeilenumbruch,
-- Wagenruecklauf, Seitenvorschub und der Vertikaltabulator (oktal \013 — \v ist keine bekannte
-- Escape-Folge und stuende sonst fuer den Buchstaben v, der dann mitgeschnitten wuerde). Damit ist
-- der Zeichensatz eine Teilmenge dessen, was strip() abschneidet: Kein Text, den der Anwendungsfall
-- durchlaesst, scheitert hier — die Reihenfolge, die ein 500 vermeidet.

CREATE TABLE angebot_kommentar (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    angebot_id bigint      NOT NULL REFERENCES angebot (id),
    text       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT angebot_kommentar_text CHECK (btrim(text, E' \t\n\r\f\013') <> ''),
    CONSTRAINT angebot_kommentar_laenge CHECK (length(text) <= 2000)
);

-- Nachgeschlagen wird immer je Angebot: die Kommentarliste in seiner Ansicht (Kriterium 1).
CREATE INDEX angebot_kommentar_angebot_idx ON angebot_kommentar (angebot_id);
