# CLAUDE.md — Projekt-Standards

Diese Datei ist der Einstiegspunkt für alle Engineering-Regeln in diesem Projekt. Sie definiert den Mindeststandard — Abweichungen sind Fehler und müssen vor dem Abschluss einer Aufgabe korrigiert werden.

---

## 🎯 Schnelleinstieg

- **Neu im Projekt?** Lies diese Datei + [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md).
- **Java/Spring-Backend arbeiten?** → [CLAUDE-java.md](CLAUDE-java.md)
- **React-Frontend arbeiten?** → [CLAUDE-react.md](CLAUDE-react.md)
- **Farben, Radien, Tiefe im Frontend?** → [CLAUDE-design.md](CLAUDE-design.md)
- **Security?** → [CLAUDE-security.md](CLAUDE-security.md)
- **Plan-Mode / Git / Issue-Workflow?** → [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md)

---

## 📚 Guide-Familie

| Guide | Fokus | Wiederverwendbar |
|---|---|---|
| **CLAUDE.md** (diese Datei) | Projekt-Übersicht + Pflichtchecks | ❌ Projekt |
| [CLAUDE-java.md](CLAUDE-java.md) | Java 25, Spring Boot 3, TDD, Coverage, Mutationstests | ✅ Allgemein |
| [CLAUDE-react.md](CLAUDE-react.md) | React 18, Vite, TypeScript, MUI, Lazy Loading, ESLint/A11y | ✅ Allgemein |
| [CLAUDE-design.md](CLAUDE-design.md) | Palette, Font, Radien, Tiefe, Kontrast von fb.crm | ❌ Projekt |
| [CLAUDE-security.md](CLAUDE-security.md) | Spring Security, JPA, Frontend-XSS, Secrets, Session-/Token-Handling | ✅ Allgemein |
| [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md) | 9-Schritte-Workflow, Issues, Git, Pflichtchecks | ✅ Allgemein |

---

## 🌐 Projektkontext

**Ziel:** **fb.crm** — ein self-hostbares Mini-CRM für Freiberufler nach [`mini-crm-spezifikation.pdf`](mini-crm-spezifikation.pdf). Seit dem Rückschnitt vom 2026-09-30 ist die Kette kurz: **Kunde** (Firma mit Ansprechpartnern) → **Angebot** (Positionen, Status, Kommentare, Anlagen) → **Rechnung** (Teilabrechnung gegen die Angebotspositionen, Nummernkreis, Festschreibung beim Stellen, PDF nach Vorlage). Auftrag, Vorgang und Pipeline aus der Spezifikation sind bewusst entfallen. Was die Spezifikation darüber hinaus beschreibt (etwa Zeiterfassung, Zahlungseingang, Auswertungen), entsteht nur über eine eigene Idee. UI im Stil eines Dashboards: linke Navigation, rechter Inhaltsbereich.

**Betriebsform: ein Rechner, ein Mandant, eine Person.** fb.crm hat genau ein Konto. Es gibt keinen Mehrbenutzerbetrieb, keine Mandanten und keine Rollen mit unterschiedlichen Rechten, und die Anwendung schützt sich nicht gegen gleichzeitiges Bearbeiten desselben Datensatzes (Entscheidung Manne, 2026-10-01, Idee #139). Technisch Zwingendes bleibt: Der Nummernkreis der Rechnung vergibt mit Zeilensperre keine Nummer doppelt.

**Nicht im Umfang** (Kapitel 01 der Spezifikation): Buchhaltung, Umsatzsteuervoranmeldung und Steuererklärung; Kalender, Aufgabenverwaltung und Terminplanung; automatischer Abgleich von Kontoumsätzen; Mehrbenutzerbetrieb, Rollen und Rechte (siehe *Betriebsform*).

**Stack:**

| Schicht | Technologie |
|---|---|
| Backend-Sprache | Java 25 (LTS) |
| Backend-Framework | Spring Boot 3.5, Spring Data JPA, Spring Web |
| Build (Backend) | Maven (inkl. `frontend-maven-plugin` für den Vite-Build) |
| Datenbank | PostgreSQL 16 |
| Objektspeicher | MinIO (S3-kompatibel) für Anlagen am Angebot und archivierte Rechnungsdokumente (Spezifikation R10) |
| Schema-Migrationen | Flyway (`db/migration/V<n>__…sql`) |
| Test (Backend) | JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit, PIT |
| Frontend-Sprache | TypeScript (`strict: true`) |
| Frontend-Framework | React 18, React Router 6 |
| Frontend-Build | Vite 5 |
| UI-Library | Material UI 6 (MUI) + Emotion |
| Test (Frontend) | Vitest + React Testing Library |
| Containerisierung | Docker (Multi-Stage: Node + Maven + JRE), Docker Compose |
| Reverse-Proxy | Caddy 2 (automatisches TLS, `https://localhost` bzw. `FBCRM_DOMAIN`) |
| Identity / Auth | Eigenes E-Mail/Passwort-Auth mit Session-Cookies (kein Keycloak/OIDC) |

**Verbindung Frontend↔Backend:** Im Dev leitet der Vite-Dev-Server (`:5173`) `/api/*` an Spring Boot auf `:8080` weiter. In Produktion serviert Spring Boot den React-Build aus `classpath:/static/` (SPA-Forwarding über eine eigene Web-Konfiguration in `config/`); davor liegt Caddy als Reverse-Proxy mit TLS. Eine Origin, kein CORS.

**Identity / Auth:** Authentifizierung ist projekteigen — kein externer Identity-Provider. Das eine Konto entsteht einmalig per Bootstrap-Token (Einrichtung), danach gibt es Anmelden, Abmelden und Passwort-Reset per Einmal-Token/Mail, sowie signierte, zustandslose Session-Tokens (HttpOnly-Cookie) mit kontogebundener Sitzungs-Generation; Passwörter mit Argon2id. Eine Registrierung gibt es nicht. Das Konto trägt die Rolle `ADMIN` (`auth.domain.Role`, einziger Wert); weitere Rollen sind nicht vorgesehen (*Betriebsform*). Regeln dazu: [CLAUDE-security.md](CLAUDE-security.md).

---

## 📂 Projektstruktur

Das Folgende ist die **Soll-Struktur**. Sie steht hier, damit jedes Arbeitspaket weiß, wohin es legt, was es baut — nicht als Bestandsaufnahme: Gerüst (`pom.xml`, `Dockerfile`, `.env.example`, `frontend/`) und fachliche Module entstehen mit dem ersten Durchstich.

```
/
├── CLAUDE*.md                          # Guide-Familie (Workflow-Guide unter .claude/)
├── mini-crm-spezifikation.pdf          # fachliche Spezifikation (Quelle des Projektziels)
├── docs/entwurf-kupferwolke.html       # Gestaltungsvorlage „Kupferwolke" (siehe CLAUDE-design.md)
├── pom.xml                             # Maven-Konfiguration (inkl. frontend-maven-plugin)
├── Dockerfile, docker-compose.yml      # Multi-Stage-Image + lokale Composition (Postgres, MinIO, Caddy)
├── docker-compose.prod.yml             # Produktions-Overlay hinter Traefik (Host aus FBCRM_DOMAIN)
├── Caddyfile                           # Reverse-Proxy + automatisches TLS
├── .env.example                        # DB-, MinIO- und App-Konfig-Vorlage
├── .claude/workflow.config.json        # issueTracker: toolbox — Issues auf dem Board (node .claude/kit/board.mjs)
├── src/main/java/org/mwolff/fbcrm/     # Backend (je Modul: domain/application/web/infrastructure)
│   ├── FbCrmApplication.java
│   ├── auth/                           # Einrichtung des Kontos, Login, Session, Passwort-Reset
│   ├── config/                         # SPA-Forwarding und sonstiges Wiring
│   ├── common/                         # SecureTokens, gemeinsame Token-Utilities
│   └── …                               # fachliche Module (firma, angebot, rechnung,
│                                       #   eigeneangaben, …) — weitere entstehen mit den Plänen
├── src/main/resources/                 # application.yml + Flyway-Migrationen
│   └── db/migration/                   # V1__baseline.sql … (Flyway-Konvention, Postgres)
├── src/test/java/org/mwolff/fbcrm/     # Tests (*Test = Unit/Slice, *IT = Testcontainers-Integration)
└── frontend/                           # React-App
    ├── package.json, vite.config.ts, tsconfig*.json
    ├── index.html
    └── src/
        ├── main.tsx, App.tsx, theme.ts
        ├── auth/                       # AuthContext (Session-basiert)
        ├── layout/                     # navItems (Einträge der Schiene)
        ├── components/                 # geteilte UI-Bausteine (AppShell mit Schiene und Kopf, …)
        ├── pages/                      # Routen-Komponenten (Auth-Seiten + fachliche Ansichten)
        ├── routes/                     # ProtectedRoute
        ├── lib/                        # reine Frontend-Hilfsfunktionen
        ├── api/                        # client.ts (fetch-Wrapper) + <domain>.ts
        └── test/                       # Vitest-Setup
```

---

## ✅ Pflichtchecks vor Abschluss einer Aufgabe

```bash
# Backend
mvn verify                              # Tests + Coverage + Mutation (siehe CLAUDE-java.md §5)

# Frontend
cd frontend && npm run build            # tsc + vite build
cd frontend && npm run lint             # ESLint + jsx-a11y
cd frontend && npm test                 # Vitest
```

Verfahren, Reporting-Format und detaillierte Schritte → [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md).

---

## ⚠️ Prioritäten bei Zielkonflikten

1. **Sicherheit**
2. **Korrektheit**
3. **Datenintegrität**
4. **Accessibility**
5. **Wartbarkeit**
6. **Testbarkeit**
7. **Performance**
8. **Visuelle Präferenz**
9. **Bequemlichkeit der Implementierung**

Keine kurzfristige Bequemlichkeit rechtfertigt unsicheren, untypisierten oder schwer wartbaren Code. Wenn Sicherheit gegen Performance abgewogen wird, gewinnt Sicherheit. Wenn Korrektheit gegen Geschwindigkeit der Lieferung abgewogen wird, gewinnt Korrektheit.

---

## 📐 Verhältnis der Guides untereinander

- **CLAUDE.md** ist die Übersicht. Konflikte zwischen den Sub-Guides werden hier geklärt.
- **CLAUDE-java.md** und **CLAUDE-react.md** beschreiben die schichtspezifischen Engineering-Regeln. Bei Widerspruch zur Sicherheit gewinnt [CLAUDE-security.md](CLAUDE-security.md).
- **CLAUDE-design.md** und **CLAUDE-react.md** teilen sich die Oberfläche: [CLAUDE-design.md](CLAUDE-design.md) regelt das *Was* (Farben, Radien, Tiefen, Kontrast), [CLAUDE-react.md](CLAUDE-react.md) das *Wie* (Theme-zentral, `sx`-Prop, keine hartcodierten Werte).
- **CLAUDE-security.md** hat in allen Sicherheitsfragen Vorrang.
- **CLAUDE-workflow.md** beschreibt das Prozess-Drumherum (Plan-Mode, Issues, Commits, GO-Freigabe, Tests). Wer Code schreibt ohne den Workflow zu befolgen, hat die Aufgabe nicht abgeschlossen.

---

**TL;DR:** fb.crm ist ein Mini-CRM für Freiberufler — Kunde, Angebot, Rechnung; ein Rechner, ein Mandant, eine Person. Java 25 + Spring Boot 3 (TDD-pflichtig, 100 % Coverage) auf PostgreSQL 16 + MinIO. React 18 + TypeScript strict + MUI im Erscheinungsbild „Kupferwolke". Eigenes Session-Auth mit genau einem Konto. Sicherheit > Korrektheit > Komfort. Vor jedem Push: `mvn verify` und `npm run build`/`lint`/`test` grün. Plan-Mode und Board-Issues sind verbindlich (siehe Workflow).

## Gedächtnis (Obsidian-Vault)

Über den MCP-Server obsidian-memory hast du Zugriff auf meinen
Gedächtnis-Vault unter /Users/manfredwolff/Nextcloud/ClaudeMemory.

- Lies zu Sessionbeginn Projekte/fb.crm/fb.crm.md (Projektstand,
  Entscheidungen, offene Punkte).
- Lies Index.md und Profil.md nur bei Bedarf.
- Wenn ich "Tagesabschluss" sage: Halte neue Entscheidungen und
  den erreichten Stand in Projekte/fb.crm/fb.crm.md fest und ergänze
  in Index.md unter "Zuletzt aktualisiert" eine Zeile.
