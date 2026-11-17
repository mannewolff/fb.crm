# fb.crm
Ein CRM für kleine Firmen

fb.crm ist ein self-hostbares Mini-CRM für eine Firma mit mehreren Personen: Kunden mit
Ansprechpartnern, Angebote mit Positionen und Rechnungen gegen diese Angebote, bis zum fertigen
Rechnungsdokument. Jede Person meldet sich mit einem eigenen Konto an, und jedes Konto hat eine von
drei Rollen: C-Level darf alles, auch Konten verwalten; Employee darf alles Fachliche, aber nicht die
Konten, die eigenen Angaben und die Administration; ReadOnly liest alles und ändert nichts. Die
genauen Rechte stehen unter [„Was jede Rolle darf"](docs/bedienung.md#was-jede-rolle-darf).

## Dokumentation

Die Dokumentation liegt in zwei Teilen unter `docs/`:

- [`docs/betrieb.md`](docs/betrieb.md) — Einrichten und Betreiben: „Voraussetzungen",
  „Konfiguration anlegen", „Starten", „Konto einrichten" (dabei entsteht das erste Konto, ein
  C-Level-Konto), „Weitere Konten anlegen", „Betrieb unter eigenem Namen", „Mailversand
  einrichten", „Hinter einem vorhandenen Traefik", „Sicherung", „Wiederherstellung" und
  „Aktualisierung".
- [`docs/bedienung.md`](docs/bedienung.md) — Bedienung: „Erste Schritte", „Gleichzeitig arbeiten",
  „Eigene Angaben", „Rechnungseinstellungen", „Firmen und Ansprechpartner", „Angebote",
  „Rechnungen", „Startseite", „Arbeitszeit", „Jahresabschlüsse", „Nachgetragene Rechnungen" und
  „Mitarbeitende und Rollen".

Vor der Einrichtung liest man hier im Projekt, beginnend mit dem Teil „Betrieb". Sobald fb.crm
läuft, steht dieselbe Dokumentation in fb.crm unter „Dokumentation".
