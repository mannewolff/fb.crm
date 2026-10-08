-- fb.crm — Anlagen am Angebot (Issue #151, Plan #150 E2, fachliche Quelle #148).
--
-- Die Anlage ist ein eigenes Fachobjekt mit eigener Identitaet und keine Liste am Angebot (E1),
-- aus demselben Grund wie der Kommentar in V12: Das Angebot wird mit seinen Positionen als Ganzes
-- geschrieben, und eine Anlage darf bei PUT /api/angebote/{id} nicht mitwandern.
--
-- Die Bytes der Anlage stehen nicht hier, sondern im Objektspeicher; objekt_schluessel zeigt
-- darauf (E3). Der Schluessel enthaelt keinen Teil des Dateinamens — der kommt von aussen und
-- waere im Schluessel eine Pfadangabe.
--
-- Kein updated_at, anders als in jeder anderen Fachtabelle (V2, V5, V6, V12): Eine Anlage aendert
-- sich nie. Umbenennen und Ersetzen sind Nicht-Ziele der Quelle; wer einen anderen Inhalt will,
-- loescht die Anlage und laedt eine neue hoch. Eine Spalte, die kein Weg je beschriebe, waere
-- Vorrat und kein Muster (CLAUDE-java.md §2.2).
--
-- Ebenso wenig gibt es eine Spalte fuer die vom Browser gemeldete Art (Content-Type des Teils):
-- Sie ist eine Eingabe von aussen und wird nirgends gelesen. Was ein Bild oder ein PDF ist,
-- entscheidet die Anwendung an den ersten Bytes des Inhalts und legt das Ergebnis in
-- vorschau_art ab (E4). Auch eine Verfasser-Spalte fehlt — Frage 8 der Quelle entscheidet gegen
-- Rechte je Verfasser, wie schon beim Kommentar.
--
-- Am Fremdschluessel steht kein ON DELETE, wie an jedem Fremdschluessel dieses Schemas: Ein
-- Angebot wird nie geloescht (Issue #127). TRUNCATE … CASCADE der Integrationstests erreicht die
-- Zeilen trotzdem.
--
-- Die beiden CHECKs sind die Zusagen, die allein die Datenbank halten kann:
--   * groesse zwischen 1 und 26.214.400 — eine leere Datei ist keine Anlage (Kriterium 5), und
--     26.214.400 Byte sind die Grenze aus Kriterium 6, angeschrieben als 25 MB (E12). Die Grenze
--     an der Schnittstelle (spring.servlet.multipart) gilt fuer die eingereichte Datei und
--     ersetzt diesen CHECK nicht — er greift auch auf einem Weg an der Anwendung vorbei.
--   * vorschau_art ist NULL oder einer der fuenf Werte des Aufzaehlungstyps Vorschauart. NULL
--     heisst „keine Vorschau", nicht der Leerstring: Eine Tabelle ist eine gueltige Anlage ohne
--     Vorschau, und die Antwort traegt dafuer vorschauArt: null.

CREATE TABLE angebot_anlage (
    id                bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    angebot_id        bigint       NOT NULL REFERENCES angebot (id),
    datei_name        varchar(255) NOT NULL,
    groesse           bigint       NOT NULL,
    vorschau_art      varchar(4),
    objekt_schluessel text         NOT NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT angebot_anlage_groesse CHECK (groesse BETWEEN 1 AND 26214400),
    CONSTRAINT angebot_anlage_vorschau_art
        CHECK (vorschau_art IS NULL OR vorschau_art IN ('PNG', 'JPEG', 'GIF', 'WEBP', 'PDF'))
);

-- Nachgeschlagen wird immer je Angebot: die Anlagenliste in seiner Ansicht (Kriterium 1).
CREATE INDEX angebot_anlage_angebot_idx ON angebot_anlage (angebot_id);
