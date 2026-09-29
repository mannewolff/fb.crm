-- fb.crm — Der Auftrag entfaellt (Issue #125).
--
-- Der Umfang wurde am 2026-09-29 verkleinert: Auf das Angebot folgt die Rechnung, ein Auftrag als
-- eigener Beleg kommt nicht mehr vor. V8 bleibt unveraendert, damit bestehende Datenbanken weiter
-- migrierbar sind; diese Migration baut ab, was V8 angelegt hat.
--
-- Die Reihenfolge folgt den Fremdschluesseln: erst die Positionen, dann der Auftrag, der sie traegt.
-- Der Nummernkreis haengt an nichts.

DROP TABLE auftrag_position;
DROP TABLE auftrag;
DROP TABLE auftrag_nummernkreis;
