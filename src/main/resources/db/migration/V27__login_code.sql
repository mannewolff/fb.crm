-- fb.crm — Der Einmalcode der Anmeldung (Plan #395, E1, E2; fachliche Quelle #392).
--
-- Der Code haengt an der Adresse, nicht am Konto: Bei der Einrichtung gibt es im Augenblick der
-- Anforderung noch kein Konto (Kriterium 6). Ein Fremdschluessel auf account wie bei
-- password_reset_token waere dort nicht fuellbar — darum gibt es keinen.
--
-- Gespeichert wird nur der Argon2id-Hash des Codes, nie der Code selbst (E2). Ein sechsstelliger
-- Code hat 10^6 Vorbilder; ein schneller Hash waere offline in Sekunden umgekehrt. Nachgeschlagen
-- wird darum ueber die Adresse, nicht ueber den Hash.

CREATE TABLE login_code (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           varchar(320) NOT NULL,
    code_hash       varchar(255) NOT NULL,
    expires_at      timestamptz  NOT NULL,
    failed_attempts integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT login_code_failed_attempts_nonnegative CHECK (failed_attempts >= 0)
);

-- Hoechstens ein Code je Adresse (E1), ohne Ruecksicht auf Gross- und Kleinschreibung wie
-- account_email_key: Ein neuer Code ersetzt den alten, statt neben ihm zu gelten.
CREATE UNIQUE INDEX login_code_email_key ON login_code (lower(email));
