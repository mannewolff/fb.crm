-- fb.crm — Der Passwort-Reset entfaellt (Issue #404; Plan #395, E18; fachliche Quelle #392).
--
-- Mit dem Code per Mail hat der Reset keinen Gegenstand mehr: Wer sein Postfach erreicht, erreicht
-- fb.crm. V1 bleibt unveraendert, damit bestehende Datenbanken weiter migrierbar sind; diese
-- Migration baut ab, was V1 fuer den Reset angelegt hat. Die Indizes fallen mit der Tabelle.
-- Die Spalte account.password_hash bleibt bis zur Umstellung der Anmeldung stehen.

DROP TABLE password_reset_token;
