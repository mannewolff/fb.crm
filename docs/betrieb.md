# Betrieb

Dieser Teil der Dokumentation beschreibt, wie man fb.crm auf einem eigenen Rechner einrichtet und
betreibt: von den Voraussetzungen über den ersten Start und das Konto bis zum Betrieb unter eigenem
Namen mit Zertifikat und zum Mailversand. Wie man mit fb.crm arbeitet, steht im Teil „Bedienung".

## Voraussetzungen

fb.crm läuft als Verbund von Containern. Nötig sind:

- ein Linux-Rechner mit Docker und Docker Compose (der Befehl `docker compose`),
- ein Git-Checkout des Projekts auf diesem Rechner; alle Befehle dieses Teils laufen im Verzeichnis
  des Checkouts,
- für den Betrieb unter eigenem Namen eine eigene Domain, deren DNS-Eintrag auf den Rechner zeigt,
  und ein Rechner, der aus dem Internet auf den Ports 80 und 443 erreichbar ist.

Alles Weitere — PostgreSQL, MinIO, die Anwendung und Caddy als Reverse-Proxy, der das Zertifikat
selbst holt — bringt `docker-compose.yml` mit.

## Konfiguration anlegen

Die Konfiguration steht in der Datei `.env` im Verzeichnis des Checkouts. Sie entsteht aus der
Vorlage:

```bash
cp .env.example .env
```

Für den lokalen Betrieb genügt ein einziger Wert: der Einmal-Schlüssel, mit dem das Konto
eingerichtet wird. Ohne ihn ist die Einrichtung abgeschaltet.

```bash
openssl rand -hex 24
```

Das Ergebnis in `.env` als `FBCRM_BOOTSTRAP_ADMIN_TOKEN=` eintragen. Alle übrigen Werte haben
Defaults für den lokalen Betrieb; was sie bewirken, steht je Variable in `.env.example`.

### Datenbank- und MinIO-Zugangsdaten

Leer gelassen gelten bekannte Vorgaben (`fbcrm` für Name,
Nutzer und Passwort der Datenbank, `fbcrm` und `fbcrm-minio` für MinIO). Auf einem Rechner, der aus
dem Internet erreichbar ist, eigene Werte setzen:

```
POSTGRES_PASSWORD=...
MINIO_ROOT_USER=...
MINIO_ROOT_PASSWORD=...
```

Ein Passwort lässt sich wieder mit `openssl rand -hex 24` erzeugen. Diese Werte vor dem ersten
Start eintragen: PostgreSQL übernimmt `POSTGRES_DB`, `POSTGRES_USER` und `POSTGRES_PASSWORD` nur
beim ersten Anlegen seiner Daten, eine spätere Änderung greift dort nicht mehr.

**Wer fb.crm unter eigenem Namen betreibt**, trägt jetzt außerdem die Werte aus dem Abschnitt
„Betrieb unter eigenem Namen" ein — vor dem ersten Start, nicht danach. Dazu gehören die drei
Pflichtwerte `FBCRM_SESSION_SECRET`, `FBCRM_BASE_URL` und `FBCRM_DEV_MODE=false`.

Compose liest die `.env` beim Erstellen der Container. Darum die Datei vollständig ausfüllen, bevor
fb.crm das erste Mal startet.

## Starten

```bash
docker compose up -d --build
```

Der erste Lauf baut das Image und spielt die Datenbankmigrationen ein; das dauert einige Minuten.
Caddy startet erst, wenn die Anwendung gesund meldet; bis dahin zeigt `docker compose ps` sie als
`starting`.

### Gesundheitsprobe

```bash
docker compose ps
```

Die Zeile von `fbcrm-api` zeigt `healthy`, sobald die Anwendung hochgefahren ist und die Migrationen
durch sind. Dieselbe Probe, die Docker dafür fährt, lässt sich von Hand aufrufen:

```bash
docker compose exec fbcrm-api curl -fsS http://localhost:8080/actuator/health
```

Die Antwort enthält `"status":"UP"`. Kommt die Anwendung nicht hoch, nennt
`docker compose logs fbcrm-api` den Grund.

## Konto einrichten

fb.crm wird von einer Firma mit mehreren Personen genutzt, jede mit einem eigenen Konto. Das erste
Konto entsteht einmalig über die Einrichtungsseite und ist ein C-Level-Konto: Es darf alles,
auch weitere Konten anlegen. Die Einrichtungsseite fragt darum nicht nach einer Rolle. Alle
weiteren Konten legt ein C-Level in fb.crm selbst an (Abschnitt „Weitere Konten anlegen").

1. Im Browser die Adresse der Instanz öffnen: lokal `https://localhost`, unter eigenem Namen die
   eigene Domain. Lokal stellt Caddy ein Zertifikat seiner eigenen Zertifizierungsstelle aus; der
   Browser warnt davor einmalig.
2. Eine frische Instanz führt auf die Seite „Einrichten". Dort den Einmal-Schlüssel aus
   `FBCRM_BOOTSTRAP_ADMIN_TOKEN`, den Anzeigenamen und die E-Mail-Adresse eingeben und „Code
   anfordern" wählen. Mit falschem Schlüssel weist die Seite die Anfrage ab, und fb.crm verschickt
   keinen Code.
3. fb.crm schickt sofort einen sechsstelligen Code. Mit eingeschaltetem Mailversand kommt er per
   Mail an die eingegebene Adresse; ohne Mailversand steht er im Protokoll des Servers (siehe
   unten).
4. Den Code in das Feld „Code" eintippen und „Einrichten" wählen. Erst jetzt entsteht das Konto,
   und man ist angemeldet.

### Code aus dem Protokoll

Ist der Mailversand ausgeschaltet, schreibt fb.crm jeden Code in das
Protokoll des Servers, bei der Einrichtung wie bei jeder späteren Anmeldung. Die Zeile lautet
„Mailversand ausgeschaltet — Einmalcode … gilt bis …" und zeigt sich mit:

```bash
docker compose logs fbcrm-api | grep Einmalcode
```

Ist der Mailversand eingeschaltet, steht kein Code im Protokoll.

Ein Code gilt 10 Minuten und nur einmal. Über „Angaben ändern" geht es zurück zum ersten Schritt,
dort fordert man einen neuen Code an. Mit eingeschaltetem Mailversand ist die Adresse damit
bestätigt: Wer sich vertippt, bekommt keinen Code, es entsteht kein Konto, und die Einrichtung
lässt sich mit der richtigen Adresse wiederholen. Mit dem Code aus dem Protokoll prüft fb.crm die
Adresse nicht. Angemeldet wird man danach mit einem Code an diese Adresse, wie im Teil „Bedienung"
unter „Erste Schritte" beschrieben.

Der Schlüssel wirkt nur, solange es noch kein Konto gibt — ein zweiter Aufruf wird abgewiesen.

### Verlorenes Postfach

Jede Anmeldung braucht einen Code an die Adresse des Kontos. Wer den
Zugang zu diesem Postfach verliert, kommt mit eingeschaltetem Mailversand nicht mehr hinein, und der
Einmal-Schlüssel greift nicht mehr, sobald ein Konto existiert. Der Weg zurück ist, die Adresse des
Kontos auf ein Postfach umzusetzen, das man erreicht:

```bash
docker compose exec postgres psql -U fbcrm -d fbcrm -c "UPDATE account SET email = 'neu@firma.de' WHERE lower(email) = lower('alt@firma.de');"
```

Dabei ist `alt@firma.de` die bisherige Adresse des Kontos und `neu@firma.de` die neue. Die neue
Adresse darf an keinem anderen Konto hängen, auch nicht an einem stillgelegten: Sonst weist die
Datenbank den Befehl ab (`account_email_key`), und nichts ändert sich. Meldet `psql` `UPDATE 0`,
stimmte die bisherige Adresse nicht. Danach meldet man sich mit einem Code an die neue Adresse an,
wie im Teil „Bedienung" unter „Erste Schritte" beschrieben; der Einmal-Schlüssel wird dafür nicht
gebraucht. Das Konto bleibt dasselbe, mit Rolle, Namen und seinen Kommentaren am Angebot. Ein Konto
wird dafür nicht gelöscht: Seine Kommentare hängen an ihm, und die Datenbank wiese das Löschen ab.
Wer `POSTGRES_USER` oder `POSTGRES_DB` in der `.env` geändert hat, setzt dort die eigenen Werte ein.

## Weitere Konten anlegen

Weitere Konten entstehen nicht über die Einrichtungsseite, sondern in fb.crm: Ein C-Level wählt im
Fuß der Schiene „Mitarbeitende" und dort „Neues Konto" (Teil „Bedienung", Abschnitt
„Mitarbeitende und Rollen"). Eine Registrierung gibt es nicht.

### Mailversand im Betrieb mit mehreren Personen

Sobald mehr als eine Person mit fb.crm arbeitet,
muss der Mailversand eingerichtet sein (Abschnitt „Mailversand einrichten"). Jede Person meldet
sich mit einem Code an, den fb.crm an ihre eigene Adresse schickt, und erfährt beim Anlegen ihres
Kontos per Begrüßungsmail, unter welcher Adresse fb.crm erreichbar ist. Diese Adresse ist
`FBCRM_BASE_URL`. Ohne Mailversand geht keine Begrüßungsmail hinaus, und die Maske sagt das nach
dem Anlegen; jeder Code steht dann weiterhin im Protokoll des Servers (Abschnitt „Konto
einrichten", „Code aus dem Protokoll"), wo ihn nur der Betreiber liest.

### Stillgelegte Konten

Ein stillgelegtes Konto bleibt in der Datenbank, mit seiner Adresse und
seinen Kommentaren. Seine Adresse lässt sich darum keinem neuen Konto geben; ein C-Level aktiviert
stattdessen das bisherige Konto wieder.

## Betrieb unter eigenem Namen

Unter eigenem Namen liefert der mitgelieferte Caddy fb.crm unter der eigenen Domain aus und holt
das Zertifikat bei Let's Encrypt selbst. Voraussetzung: Der DNS-Eintrag der Domain zeigt auf den
Rechner, und die Ports 80 und 443 sind aus dem Internet erreichbar.

**Pflicht vor dem ersten Start von außen.** Sobald fb.crm von außen erreichbar ist, müssen diese
drei Werte in der `.env` gesetzt sein. Ohne sie läuft fb.crm mit einem öffentlich bekannten
Signaturgeheimnis, und jeder könnte sich eine gültige Sitzung bauen:

- `FBCRM_SESSION_SECRET` — ein eigenes Signaturgeheimnis für die Sitzungen, erzeugt mit
  `openssl rand -base64 48`,
- `FBCRM_BASE_URL` — die öffentliche Adresse der Instanz, etwa `https://crm.example.org`; die Mail
  mit dem Code nennt sie als Klartext, damit erkennbar ist, zu welcher Instanz der Code gehört,
- `FBCRM_DEV_MODE=false` — schaltet den Entwicklungsmodus ab; danach verweigert fb.crm den Start
  mit dem voreingestellten Signaturgeheimnis.

Dazu kommen die beiden Werte für Caddy:

- `FBCRM_DOMAIN` — die eigene Domain ohne `https://`, etwa `crm.example.org`,
- `FBCRM_ACME_EMAIL` — eine Kontaktadresse für Let's Encrypt.

Zusammen sieht das in der `.env` etwa so aus:

```
FBCRM_SESSION_SECRET=...
FBCRM_BASE_URL=https://crm.example.org
FBCRM_DEV_MODE=false
FBCRM_DOMAIN=crm.example.org
FBCRM_ACME_EMAIL=admin@example.org
```

Danach starten, wie im Abschnitt „Starten" beschrieben. Caddy holt das Zertifikat beim ersten
Aufruf der Domain selbst; der Browser zeigt danach keine Warnung. Den Abschnitt „Konto einrichten"
dann unter der eigenen Domain durchlaufen.

**Eine Änderung der `.env` wirkt erst nach `docker compose up -d`**, nicht nach
`docker compose restart`: Compose liest die `.env` beim Erstellen der Container, und `restart`
behält die Umgebung, mit der ein Container einmal angelegt wurde. Wer die Werte erst nach dem
ersten Start einträgt, ruft darum

```bash
docker compose up -d
```

auf. Compose erstellt dann die betroffenen Container neu.

## Mailversand einrichten

fb.crm verschickt den Code für die Anmeldung und die Einrichtung per Mail. Der Mailversand ist zu
Beginn abgeschaltet; solange er aus ist, steht jeder Code im Protokoll des Servers (Abschnitt „Konto
einrichten", „Code aus dem Protokoll"). Der Versand ist damit keine Voraussetzung für den Zugang.
Eingeschaltet wird er in der `.env`:

- `FBCRM_MAIL_ENABLED=true` — schaltet den Versand ein,
- `FBCRM_MAIL_FROM` — die Absenderadresse der Mails,
- `FBCRM_SMTP_HOST` und `FBCRM_SMTP_PORT` — der SMTP-Server des eigenen Mailanbieters,
- `FBCRM_SMTP_USER` und `FBCRM_SMTP_PASSWORD` — die Zugangsdaten dort; leer, wenn der Server
  keine Anmeldung verlangt,
- `FBCRM_SMTP_AUTH` und `FBCRM_SMTP_STARTTLS` — ob der Server Anmeldung beziehungsweise STARTTLS
  verlangt; leer gilt `true`.

Ein Beispiel für einen Anbieter mit Anmeldung und STARTTLS auf Port 587:

```
FBCRM_MAIL_ENABLED=true
FBCRM_MAIL_FROM=crm@example.org
FBCRM_SMTP_HOST=smtp.example.org
FBCRM_SMTP_PORT=587
FBCRM_SMTP_USER=crm@example.org
FBCRM_SMTP_PASSWORD=...
```

**Eine Änderung der `.env` wirkt erst nach `docker compose up -d`**, nicht nach
`docker compose restart`. Nach dem Eintragen der Mailwerte also:

```bash
docker compose up -d
```

Ob der Versand klappt, zeigt die Anmeldung per Code (Teil „Bedienung", „Erste Schritte"): Kommt
die Mail mit dem Code an, ist der Mailversand eingerichtet. Ab dann steht kein Code mehr im
Protokoll.

### Lokal ausprobieren mit Mailpit

Lokal gibt es keinen Mailserver. Den Auffangserver Mailpit
bringt das Profil `dev-mail` mit; er nimmt jede Mail an, stellt keine zu und zeigt sie unter
`http://localhost:8025`. Dafür in der `.env`:

```
FBCRM_MAIL_ENABLED=true
FBCRM_SMTP_AUTH=false
FBCRM_SMTP_STARTTLS=false
```

`FBCRM_SMTP_HOST` und `FBCRM_SMTP_PORT` bleiben leer; ihr Default zeigt bereits auf Mailpit. Dann
mit dem Profil starten:

```bash
docker compose --profile dev-mail up -d
```

## Hinter einem vorhandenen Traefik

Wer auf dem Rechner bereits einen Traefik betreibt, lässt Caddy weg; TLS terminiert dann Traefik.
Das Produktions-Overlay `docker-compose.prod.yml` hängt die Anwendung an das externe Netz `web` des
Traefik und startet Caddy nicht. Aufruf:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

**Nicht ohne Overlay starten.** Ein `docker compose up -d --build` ohne `-f` liest nur
`docker-compose.yml`: Die Anwendung hängt dann nicht am Netz `web`, trägt keine Traefik-Labels und
läuft mit `FBCRM_DEV_MODE=true`; Traefik antwortet auf die Domain mit 404, und Caddy startet mit.
Wer den kurzen Aufruf gewohnt ist, trägt beide Dateien in die Server-`.env` ein — Compose liest sie
dann bei jedem Aufruf in diesem Verzeichnis, auch bei `ps`, `logs` und `stop`:

```
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml
```

Lief der Stack doch einmal ohne Overlay, erst Caddy entfernen, dann mit Overlay neu starten:

```bash
docker compose rm -sf caddy
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Pflicht in der Server-`.env` sind `FBCRM_SESSION_SECRET` (`openssl rand -base64 48`),
`FBCRM_BASE_URL` und `FBCRM_DOMAIN`; ohne sie bricht schon `docker compose config` ab. Das
Overlay setzt `FBCRM_DEV_MODE` fest auf `false` — mit dem voreingestellten Secret startet die
Anwendung dort nicht. Auch hier wirkt eine Änderung der `.env` erst nach dem Aufruf oben, nicht
nach `docker compose restart`.

### Prüfschritt X-Forwarded-For

Die Anwendung zählt Fehlversuche bei der Anmeldung je
Absenderadresse. Hinter einem Proxy kennt sie die Adresse nur aus `X-Forwarded-For` — und glaubt
dem Kopf ausschließlich, wenn die Gegenstelle in `FBCRM_TRUSTED_PROXIES` steht. Traefiks
Vertrauenskonfiguration liegt nicht in diesem Repository. Vor dem Eintrag der Traefik-Adresse
deshalb prüfen, dass Traefik eingehende `X-Forwarded-*`-Köpfe fremder Quellen verwirft
(`forwardedHeaders.trustedIPs` am Entrypoint nicht auf beliebige Quellen gesetzt, kein
`forwardedHeaders.insecure`). Sonst setzt ein Angreifer den Kopf selbst und umgeht die
Zählbremse. Solange das nicht geprüft ist, `FBCRM_TRUSTED_PROXIES` leer lassen: Dann zählen alle
Anfragen unter Traefiks Adresse — strenger als nötig, aber nicht umgehbar.

## Sicherung

Eine vollständige Sicherung von fb.crm besteht aus drei Teilen: der Datenbank, den abgelegten
Dokumenten (Anlagen am Angebot, gestellte Rechnungen, Briefpapier) und der Konfiguration mit ihren
Zugangsdaten. Nur alle drei zusammen lassen sich zurückspielen. Die Befehle laufen im Verzeichnis
des Checkouts, fb.crm läuft zu Beginn.

### Schritt 1: Verzeichnis anlegen und Version vermerken

Die Wiederherstellung braucht dieselbe
Version, auf der fb.crm beim Sichern läuft.

```bash
mkdir -p sicherung
git describe --tags > sicherung/version.txt
```

### Schritt 2: Anwendung anhalten

Die Anwendung anhalten, damit sich während der Sicherung nichts ändert. Datenbank und
Objektspeicher laufen weiter.

```bash
docker compose stop caddy fbcrm-api
```

### Schritt 3: Datenbank in eine Datei schreiben

```bash
docker compose exec -T postgres pg_dump -U fbcrm -d fbcrm > sicherung/datenbank.sql
```

Wer `POSTGRES_USER` oder `POSTGRES_DB` in der `.env` geändert hat, setzt dort die eigenen Werte ein.

### Schritt 4: Abgelegte Dokumente kopieren

Sie liegen im Band `minio_data`, dem Datenverzeichnis
des Objektspeichers. Den Objektspeicher anhalten und das Verzeichnis herauskopieren:

```bash
docker compose stop minio
docker compose cp minio:/data sicherung/minio_data
```

### Schritt 5: Konfiguration sichern

Die `.env` mit allen Zugangsdaten:

```bash
cp .env sicherung/env
```

### Schritt 6: fb.crm wieder starten

```bash
docker compose up -d
```

### Wo die Sicherung landet

Die Dateien liegen danach im Verzeichnis `sicherung` des Checkouts:
`version.txt`, `datenbank.sql`, das Verzeichnis `minio_data` und `env`. Dort darf die Sicherung
nicht bleiben, denn fällt der Rechner aus, ist sie mit ihm verloren. Das Verzeichnis gehört auf
einen anderen Rechner oder ein externes Medium, am einfachsten als Archiv:

```bash
tar czf fbcrm-sicherung-$(date +%F).tar.gz sicherung
```

### Die Sicherung enthält die Geheimnisse

In ihr stehen das Signaturgeheimnis der Sitzungen, die
Passwörter von Datenbank, Objektspeicher und Mailserver und alle Kundendaten. Sie liegt darum nur
dort, wo auch die `.env` liegen dürfte: verschlüsselt oder an einem Ort, auf den außer dem
Betreiber niemand Zugriff hat. Nach dem Wegkopieren das Verzeichnis `sicherung` und das Archiv vom
Rechner löschen.

Das Band `postgres_data` wird nicht als Ganzes kopiert; sein Inhalt steckt vollständig in
`datenbank.sql`. Die Bänder `caddy_data` und `caddy_config` brauchen keine Sicherung: Caddy holt
sein Zertifikat nach einer Wiederherstellung selbst neu.

## Wiederherstellung

Dieser Weg spielt eine Sicherung auf einem neuen Rechner zurück, ohne Zugriff auf den alten. Nötig
sind nur die Dateien aus dem Abschnitt „Sicherung" und ein Rechner, der die Voraussetzungen aus dem
Abschnitt „Voraussetzungen" erfüllt. Unter eigenem Namen zeigt der DNS-Eintrag der Domain vorher
auf den neuen Rechner.

### Schritt 1: Projekt auschecken, dieselbe Versionsmarke

Die Marke steht in `version.txt` der
Sicherung, etwa `v1.2.0`. `ADRESSE` ist die Adresse, unter der das Projekt bezogen wird:

```bash
git clone ADRESSE fb-crm
cd fb-crm
git checkout v1.2.0
```

### Schritt 2: Sicherung hineinlegen

Im Verzeichnis des Checkouts liegt danach ein Verzeichnis
`sicherung` mit `datenbank.sql`, `minio_data` und `env`. Ein Archiv wird dort ausgepackt, etwa:

```bash
tar xzf fbcrm-sicherung-2026-10-20.tar.gz
```

### Schritt 3: .env zurücklegen

Die `.env` gehört vor dem ersten Start zurück, denn PostgreSQL übernimmt Name, Nutzer
und Passwort der Datenbank nur beim ersten Anlegen seiner Daten:

```bash
cp sicherung/env .env
```

### Schritt 4: Bänder anlegen

Datenbank und Objektspeicher starten, die Anwendung noch nicht;
Compose legt dabei die Bänder `postgres_data` und `minio_data` leer an:

```bash
docker compose up -d postgres minio
```

Weiter, wenn `docker compose ps` beide als `healthy` zeigt.

### Schritt 5: Datenbank einspielen

```bash
docker compose exec -T postgres psql -U fbcrm -d fbcrm -v ON_ERROR_STOP=1 < sicherung/datenbank.sql
```

Der Befehl muss ohne Fehlermeldung durchlaufen. Wer `POSTGRES_USER` oder `POSTGRES_DB` geändert
hat, setzt die eigenen Werte ein.

### Schritt 6: Dokumente zurückschreiben

Den Objektspeicher anhalten, das Datenverzeichnis in das
Band `minio_data` kopieren und den Dateien den Eigentümer geben, unter dem MinIO läuft:

```bash
docker compose stop minio
docker compose cp sicherung/minio_data/. minio:/data
docker compose run --rm --user 0:0 --entrypoint /usr/bin/chown minio -R 65532:65532 /data
```

### Schritt 7: Starten

```bash
docker compose up -d --build
```

Der Start dauert wie beim ersten Mal einige Minuten. Weiter, wenn `docker compose ps` die Zeile von
`fbcrm-api` als `healthy` zeigt.

### Schritt 8: Prüfen

- Mit dem bisherigen Konto anmelden; die Seite „Einrichten" erscheint nicht.
- Kunden, Angebote mit ihren Anlagen, Rechnungen samt Rechnungsdokument und das Briefpapier
  öffnen: Alles ist wieder da.
- Den Mailversand über die Anmeldung per Code prüfen (Teil „Bedienung", „Erste Schritte"): Kommt
  die Mail mit dem Code an, funktioniert er.

Danach das Verzeichnis `sicherung` und das Archiv vom neuen Rechner löschen; sie enthalten die
Geheimnisse.

## Aktualisierung

Eine **veröffentlichte Version** von fb.crm ist ein Stand mit einer Versionsmarke der Form
`vX.Y.Z`, etwa `v1.2.0`. Aktualisiert wird von der unmittelbar vorherigen veröffentlichten Version
auf die neue, indem der Checkout auf die Marke der neuen Version geht. Die Datenbank passt fb.crm
beim Start selbst an.

### Schritt 1: Ausgangsversion vermerken

```bash
git describe --tags
```

### Schritt 2: Sichern

Eine Sicherung anlegen, wie im Abschnitt „Sicherung" beschrieben, und vom
Rechner wegkopieren. Ohne Sicherung nicht aktualisieren: Sie ist der Weg zurück, falls die neue
Version nicht hochkommt.

### Schritt 3: Neue Marke auschecken

Die Marken holen und den Checkout auf die Marke `vX.Y.Z` der
neuen Version bringen, etwa `v1.3.0`:

```bash
git fetch --tags
git checkout v1.3.0
```

### Schritt 4: Neu bauen und starten

```bash
docker compose up -d --build
```

Weiter, wenn `docker compose ps` die Zeile von `fbcrm-api` als `healthy` zeigt. Kommt die Anwendung
nicht hoch, nennt `docker compose logs fbcrm-api` den Grund.

### Schritt 5: Prüfen

- Unter der Marke in der Seitenleiste steht die neue Versionsnummer.
- Kunden, Angebote und Rechnungen sind unverändert da.
- Beim Update auf die erste Version mit der Anmeldung per Code enden alle laufenden Sitzungen einmal;
  E-Mail-Adresse und Anzeigename des Kontos bleiben, wie sie waren. Die nächste Anmeldung läuft
  per Code an die Adresse des Kontos (Teil „Bedienung", „Erste Schritte"). Ist der Mailversand
  ausgeschaltet, steht der Code im Protokoll des Servers (Abschnitt „Konto einrichten", „Code aus
  dem Protokoll").
- Beim Update auf die erste Version mit mehreren Konten und Rollen enden alle laufenden Sitzungen
  ebenfalls einmal, weil die Rollen neu sind. Das bisherige Konto ist danach ein C-Level-Konto mit
  unveränderter E-Mail-Adresse und unverändertem Anzeigenamen; die nächste Anmeldung läuft wie
  gewohnt per Code.

### Schritt 6: Ausgangs- und Zielversion vermerken

Den Wechsel vermerken, etwa „am 2026-10-20 von v1.2.0 auf v1.3.0
aktualisiert". Der Vermerk sagt beim nächsten Mal, von welcher Version aus aktualisiert wird.
