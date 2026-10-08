-- fb.crm — Baseline des Datenbankschemas.
--
-- Konto, Passwort-Reset-Token und Postausgangsfach entstehen in einem Zug, damit die folgenden
-- Pakete keine Migrationskette auf ein halbes Schema setzen.

CREATE TABLE account (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email              varchar(320) NOT NULL,
    display_name       varchar(200) NOT NULL,
    password_hash      varchar(255) NOT NULL,
    role               varchar(32)  NOT NULL,
    session_generation integer      NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT account_role_known CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT account_session_generation_nonnegative CHECK (session_generation >= 0)
);

-- Adressen sind ohne Ruecksicht auf Gross- und Kleinschreibung eindeutig: sonst legten
-- "manne@example.org" und "Manne@Example.org" zwei Konten an derselben Postadresse an.
CREATE UNIQUE INDEX account_email_key ON account (lower(email));

-- Genau ein Plattform-Admin, erzwungen von der Datenbank. Der Index steht auf dem konstanten
-- Ausdruck (true) und traegt damit fuer alle ADMIN-Zeilen denselben Schluessel — deshalb
-- schliesst er auch das Wettrennen zweier gleichzeitiger Einrichtungsaufrufe mit
-- verschiedenen Adressen, das ein Index auf email nicht sehen wuerde.
CREATE UNIQUE INDEX account_single_admin ON account ((true)) WHERE role = 'ADMIN';

CREATE TABLE password_reset_token (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id bigint      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Nachgeschlagen wird ueber den Hash; das Token selbst wird nie gespeichert.
CREATE UNIQUE INDEX password_reset_token_hash_key ON password_reset_token (token_hash);
CREATE INDEX password_reset_token_account_idx ON password_reset_token (account_id);

CREATE TABLE outbox_message (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient       varchar(320) NOT NULL,
    subject         varchar(255) NOT NULL,
    body            text         NOT NULL,
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    sent_at         timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT outbox_message_attempts_nonnegative CHECK (attempts >= 0)
);

-- Der Versand fragt nur nach dem, was noch offen ist.
CREATE INDEX outbox_message_pending_idx ON outbox_message (next_attempt_at) WHERE sent_at IS NULL;
