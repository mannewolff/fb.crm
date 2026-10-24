-- fb.crm — Eine geloeschte Rechnung haelt ihre Nummer nicht fest (Issue #439; Plan #415, E17).
--
-- Seit V34 bleibt ein geloeschter Rechnungsentwurf und eine geloeschte nachgetragene Rechnung als
-- gekennzeichnete Zeile stehen. Die eindeutigen Indizes ueber lower(nummer) aus V22 saehen diese
-- Zeile weiter und wiesen eine neue Rechnung mit derselben Nummer ab, obwohl die alte fuer jeden
-- Leseweg fort ist. Darum gelten beide Indizes nur noch fuer Zeilen ohne geloescht_am.
--
-- Die Schritte und ihr Grund:
--
-- 1. Den Index aus V22 entfernen — ein bestehender Index laesst sich nicht um eine Bedingung
--    ergaenzen.
-- 2. Ihn unter demselben Namen mit WHERE geloescht_am IS NULL neu anlegen. Der Name bleibt, weil
--    die Repositories der Anwendung ihn nennen und die Tests ihn in der Verletzungsmeldung
--    erwarten. Da bisher keine Zeile gekennzeichnet ist, ist der Bestand unter dem neuen Index so
--    eindeutig wie unter dem alten, und der Aufbau kann nicht scheitern.
--
-- Die Migration verliert keine Daten. Der Weg zurueck ist die Sicherung, die docs/betrieb.md unter
-- „Aktualisierung" vor jedem Update verlangt, oder beide Indizes ohne WHERE neu anlegen wie in V22 —
-- das scheitert, sobald eine geloeschte und eine lebende Rechnung dieselbe Nummer tragen.

DROP INDEX rechnung_nummer_lower_key;

CREATE UNIQUE INDEX rechnung_nummer_lower_key ON rechnung (lower(nummer))
    WHERE geloescht_am IS NULL;

DROP INDEX rechnung_nachgetragen_nummer_lower_key;

CREATE UNIQUE INDEX rechnung_nachgetragen_nummer_lower_key
    ON rechnung_nachgetragen (lower(nummer)) WHERE geloescht_am IS NULL;
