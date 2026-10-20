-- fb.crm — Das Passwort entfaellt, die Anmeldung laeuft per Code (Issue #405; Plan #395, E18;
-- fachliche Quelle #392, Kriterium 8).
--
-- Die Spalte wird geloescht, nicht geleert: Eine leere Passwortspalte waere ein zweiter Weg, der
-- nur auf seinen Inhalt wartet (Nicht-Ziel „Kein Passwort als zweiter Weg"). Der Weg zurueck ist
-- die Sicherung, die docs/betrieb.md unter „Aktualisierung" vor jedem Update verlangt — aus dieser
-- Migration selbst fuehrt keiner zurueck.
--
-- Das Hochzaehlen der Sitzungs-Generation beendet jede bestehende Sitzung einmal: Jedes vorher
-- ausgestellte Session-Token traegt danach eine veraltete Generation (E7). E-Mail-Adresse und
-- Anzeigename bleiben, wie sie waren.

ALTER TABLE account DROP COLUMN password_hash;

UPDATE account SET session_generation = session_generation + 1;
