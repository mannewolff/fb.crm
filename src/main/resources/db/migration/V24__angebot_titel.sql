-- fb.crm — Das Angebot bekommt einen Titel (Issue #316; Plan #315; fachliche Quelle #314).
--
-- Angebote derselben Firma vom selben Tag und mit demselben Status sind auf der Firmenseite nicht
-- auseinanderzuhalten. Der Titel ist die kurze, einzeilige Benennung neben der mehrzeiligen
-- Beschreibung und hoechstens 200 Zeichen lang (#314, Kriterium 1).
--
-- E3 — Die Spalte bleibt nullable, ohne DEFAULT und ohne Nachtrag auf bestehende Zeilen: Ein
-- bestehendes Angebot hat keinen Titel und bleibt trotzdem gueltig (#314, Kriterium 4). Eine
-- NOT-NULL-Spalte brauchte genau die automatische Vorbelegung, die die fachliche Quelle ausschliesst.
--
-- Der CHECK angebot_titel haelt dieselbe Zusage wie der kompakte Konstruktor von Angebot (E1): Ein
-- Titel ist nicht leer. Zwei Huerden fuer eine Regel ist hier Absicht, nach dem Muster von
-- angebot_art_status in V20 — die Domaene weist frueh und mit einer lesbaren Meldung ab, die
-- Datenbank laesst auch auf dem Weg daran vorbei (Migration, Konsole) keinen Titel aus Leerzeichen zu.

ALTER TABLE angebot ADD COLUMN titel varchar(200);

ALTER TABLE angebot ADD CONSTRAINT angebot_titel CHECK (titel IS NULL OR btrim(titel) <> '');
