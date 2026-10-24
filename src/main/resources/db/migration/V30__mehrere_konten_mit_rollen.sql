-- fb.crm — Mehrere Konten mit drei Rollen (Issue #417; Plan #414, A3 bis A5; fachliche Quelle #409,
-- Kriterium 2).
--
-- Die Schritte in ihrer Reihenfolge und ihr Grund:
--
-- 1. Der alte CHECK account_role_known faellt zuerst: Er liesse C_LEVEL nicht zu, und der neue
--    liesse die alte Zeile mit ADMIN nicht stehen. Alles laeuft in der einen Transaktion der
--    Migration; einen Stand ohne CHECK sieht niemand ausserhalb.
-- 2. Das bisherige Konto wird C-Level. Zeilen mit USER gibt es im Bestand nicht: Die Anwendung
--    kannte nur ADMIN und haette eine solche Zeile nicht lesen koennen.
-- 3. Der Index account_single_admin entfaellt ersatzlos. Er trug die Zusage „genau ein Konto", die
--    mit mehreren Personen aufgehoben ist; den Riegel der Einrichtung haelt kuenftig die Frage nach
--    irgendeinem Konto (A5).
-- 4. Der neue CHECK account_role_known laesst nur noch die drei neuen Namen zu. Ein CHECK, der
--    ADMIN oder USER weiter zuliesse, liesse eine Zeile entstehen, die nichts im Code mehr deutet
--    (A4).
-- 5. Die Spalte aktiv traegt, ob ein Konto stillgelegt ist. Stillgelegt wird statt geloescht; der
--    Bestand ist aktiv.
-- 6. Das Hochzaehlen der Sitzungs-Generation beendet jede bestehende Sitzung einmal, wie in V29:
--    Die Rollen sind neu, und die naechste Anmeldung laeuft per Code.
--
-- Die Migration verliert keine Daten. Der Weg zurueck ist die Sicherung, die docs/betrieb.md unter
-- „Aktualisierung" vor jedem Update verlangt — aus dieser Migration selbst fuehrt keiner zurueck.

ALTER TABLE account DROP CONSTRAINT account_role_known;

UPDATE account SET role = 'C_LEVEL' WHERE role = 'ADMIN';

DROP INDEX account_single_admin;

ALTER TABLE account
    ADD CONSTRAINT account_role_known CHECK (role IN ('C_LEVEL', 'EMPLOYEE', 'READ_ONLY'));

ALTER TABLE account ADD COLUMN aktiv boolean NOT NULL DEFAULT true;

UPDATE account SET session_generation = session_generation + 1;
