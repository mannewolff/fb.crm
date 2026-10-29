-- fb.crm — Die Belegkopie mit Bankfeldern und dem Kuerzel des Landes (Issue #467; Plan #461, E10
-- und E16; fachliche Quelle #460, Kriterium 8).
--
-- Die gestellte Rechnung kopiert die eigene Bankverbindung jetzt in festen Feldern, wie die eigenen
-- Angaben sie seit Version 37 fuehren: IBAN ohne Leerzeichen, BIC und Kontoinhaber. Eine neue
-- Rechnung schreibt absender_bankverbindung nicht mehr; alte Rechnungen behalten dort ihren Text,
-- und ihr Dokument bleibt, wie es war (Kriterium 5).
--
-- empfaenger_land_kuerzel traegt das Kuerzel nach ISO 3166-1 fuer die E-Rechnung; empfaenger_land
-- traegt weiter den gedruckten Namen. Eine Spalte fuer beides gibt es nicht: Die alten Rechnungen
-- tragen dort Freitext, und eine Spalte mit zwei Bedeutungen verletzte die Datenintegritaet (E10).
--
-- Alle vier Spalten sind nullbar, weil alte Rechnungen und Entwuerfe sie nicht fuellen. Die Laengen
-- folgen eigene_angaben (Version 37) und firma.land (Version 36).
--
-- Die Migration verliert keine Daten. Der Weg zurueck ist
-- ALTER TABLE rechnung DROP COLUMN empfaenger_land_kuerzel, DROP COLUMN absender_kontoinhaber,
-- DROP COLUMN absender_bic, DROP COLUMN absender_iban.

ALTER TABLE rechnung
    ADD COLUMN absender_iban varchar(34),
    ADD COLUMN absender_bic varchar(11),
    ADD COLUMN absender_kontoinhaber varchar(200),
    ADD COLUMN empfaenger_land_kuerzel varchar(2);
