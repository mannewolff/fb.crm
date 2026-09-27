-- fb.crm — Das Ereignis als dritte Eintragsart der Historie (Plan #87, E3; Kriterium 19).
--
-- Ein Zustandswechsel eines Dokuments erscheint in derselben Folge wie Kommentar und Anhang: Die
-- Historie ist nach E6 *eine* nach Zeitpunkt sortierte Reihe, eine zweite Tabelle verlangte eine
-- Mischsortierung in der Anwendung. Ein Ereignis traegt Text und nie eine Datei; geschrieben wird
-- es allein von der Anwendung, nie von aussen (EreignisVermerkenUseCase).
--
-- Die beiden Checks aus V3 werden ersetzt und nicht ergaenzt: Sie nennen die jeweils andere Art
-- ausdruecklich, und genau das soll so bleiben. Jeder der drei Checks laesst die beiden anderen
-- Arten durch und prueft seine eigene vollstaendig — damit faellt eine vierte Art durch alle drei,
-- ohne dass "art" eine eigene Aufzaehlung braucht. V3 selbst bleibt unberuehrt; Flyway prueft die
-- Pruefsumme angewendeter Migrationen.

ALTER TABLE vorgang_eintrag DROP CONSTRAINT vorgang_eintrag_kommentar;
ALTER TABLE vorgang_eintrag DROP CONSTRAINT vorgang_eintrag_anhang;

ALTER TABLE vorgang_eintrag
    ADD CONSTRAINT vorgang_eintrag_kommentar CHECK (
        art IN ('ANHANG', 'EREIGNIS')
            OR (art = 'KOMMENTAR'
                AND text IS NOT NULL AND btrim(text) <> ''
                AND datei_name IS NULL
                AND datei_groesse IS NULL
                AND objekt_schluessel IS NULL));

ALTER TABLE vorgang_eintrag
    ADD CONSTRAINT vorgang_eintrag_anhang CHECK (
        art IN ('KOMMENTAR', 'EREIGNIS')
            OR (art = 'ANHANG'
                AND datei_name IS NOT NULL
                AND datei_groesse IS NOT NULL
                AND objekt_schluessel IS NOT NULL));

ALTER TABLE vorgang_eintrag
    ADD CONSTRAINT vorgang_eintrag_ereignis CHECK (
        art IN ('KOMMENTAR', 'ANHANG')
            OR (art = 'EREIGNIS'
                AND text IS NOT NULL AND btrim(text) <> ''
                AND datei_name IS NULL
                AND datei_groesse IS NULL
                AND objekt_schluessel IS NULL));
