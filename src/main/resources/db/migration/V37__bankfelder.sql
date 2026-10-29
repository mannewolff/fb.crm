-- fb.crm — Die Bankverbindung in Feldern: IBAN, BIC und Kontoinhaber (Issue #465; Plan #461, E13;
-- fachliche Quelle #460, Kriterien 7 und 11).
--
-- Die E-Rechnung braucht die eigene Bankverbindung in fester Form (Zahlungsangaben BT-84 bis
-- BT-86). Die drei Spalten entstehen leer; gefuellt werden sie aus dem bisherigen Freitext von der
-- Java-Migration BankverbindungInFeldernMigration (Version 38, E14), soweit er eine gueltige IBAN
-- hergibt.
--
-- Die Laengen: 34 Zeichen sind die laengste IBAN, 11 die laengste BIC, und der Kontoinhaber ist
-- so lang wie der Name, der ihn beim Umstellen liefert. Die IBAN steht ohne Leerzeichen in
-- Grossbuchstaben; gedruckt wird sie in Vierergruppen. Einen CHECK auf ihr Format gibt es nicht —
-- die Pruefziffer prueft die Anwendung (E15), und ein halber Riegel in der Datenbank waere eine
-- zweite Stelle fuer dieselbe Regel.
--
-- Die Spalte bankverbindung bleibt als „bisherige Bankverbindung" stehen (E13): Die Maske zeigt
-- sie, bis IBAN und Kontoinhaber gefuellt sind, und erst dann leert die Anwendung sie.
--
-- Die Migration verliert keine Daten. Der Weg zurueck ist
-- ALTER TABLE eigene_angaben DROP COLUMN kontoinhaber, DROP COLUMN bic, DROP COLUMN iban.

ALTER TABLE eigene_angaben
    ADD COLUMN iban varchar(34),
    ADD COLUMN bic varchar(11),
    ADD COLUMN kontoinhaber varchar(200);
