# fb.crm
Ein CRM für Freiberufler

## Inbetriebnahme

Voraussetzung ist Docker mit Compose. Alles Weitere — PostgreSQL, MinIO, die Anwendung und Caddy
als TLS-Proxy — bringt `docker-compose.yml` mit.

### 1. Konfiguration anlegen

```bash
cp .env.example .env
```

Für den lokalen Betrieb genügt ein einziger Wert: der Einmal-Schlüssel, mit dem das erste Konto
eingerichtet wird. Ohne ihn ist die Einrichtung abgeschaltet.

```bash
openssl rand -hex 24
```

Das Ergebnis in `.env` als `FBCRM_BOOTSTRAP_ADMIN_TOKEN=` eintragen. Alle übrigen Werte haben
Defaults für den lokalen Betrieb; was sie bewirken, steht je Variable in `.env.example`.

### 2. Stack starten

```bash
docker compose up -d --build
```

Der erste Lauf baut das Image und spielt die Datenbankmigrationen ein. Caddy startet erst, wenn
die Anwendung gesund meldet; bis dahin zeigt `docker compose ps` sie als `starting`.

### 3. Aufrufen und einrichten

`https://localhost` im Browser öffnen. Caddy stellt lokal ein Zertifikat seiner eigenen
Zertifizierungsstelle aus; der Browser warnt davor einmalig.

Eine frische Instanz führt auf die Einrichtungsseite. Dort den Einmal-Schlüssel aus Schritt 1
eingeben, dazu E-Mail-Adresse, Anzeigename und Passwort. Danach ist man angemeldet. Der Schlüssel
wirkt nur, solange es noch kein Konto gibt — ein zweiter Aufruf wird abgewiesen.

### Vertippte E-Mail-Adresse bei der Einrichtung

Eine Profilbearbeitung gibt es noch nicht, und der Einmal-Schlüssel greift nicht mehr, sobald ein
Konto existiert. Wer sich bei der Einrichtung in der Adresse vertippt hat, ist damit ausgesperrt.
Der Weg zurück ist das Löschen der Kontozeile:

```bash
docker compose exec postgres psql -U fbcrm -d fbcrm -c "DELETE FROM account;"
```

Danach greift der Schlüssel wieder, und die Einrichtung läuft erneut. Der Befehl entfernt **jedes**
Konto samt offener Passwort-Reset-Links — nur auf einer frisch eingerichteten Instanz ausführen.
Wer `POSTGRES_USER` oder `POSTGRES_DB` in der `.env` geändert hat, setzt dort die eigenen Werte ein.

### Passwort-Reset lokal ausprobieren

Lokal gibt es keinen Mailserver. Den Auffangserver Mailpit bringt das Profil `dev-mail` mit; er
nimmt jede Mail an, stellt keine zu und zeigt sie unter `http://localhost:8025`. Dafür in der
`.env`:

```
FBCRM_MAIL_ENABLED=true
FBCRM_SMTP_AUTH=false
FBCRM_SMTP_STARTTLS=false
```

und mit dem Profil starten:

```bash
docker compose --profile dev-mail up -d
```

### Produktion hinter Traefik

Auf dem Server läuft Caddy nicht; TLS terminiert der dort vorhandene Traefik. Aufruf:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Pflicht in der Server-`.env` sind `FBCRM_SESSION_SECRET` (`openssl rand -base64 48`),
`FBCRM_BASE_URL` und `FBCRM_DOMAIN`; ohne sie bricht schon `docker compose config` ab. Das
Overlay setzt `FBCRM_DEV_MODE` fest auf `false` — mit dem voreingestellten Secret startet die
Anwendung dort nicht.

**Prüfschritt `X-Forwarded-For`.** Die Anwendung zählt Fehlversuche bei der Anmeldung je
Absenderadresse. Hinter einem Proxy kennt sie die Adresse nur aus `X-Forwarded-For` — und glaubt
dem Kopf ausschließlich, wenn die Gegenstelle in `FBCRM_TRUSTED_PROXIES` steht. Traefiks
Vertrauenskonfiguration liegt nicht in diesem Repository. Vor dem Eintrag der Traefik-Adresse
deshalb prüfen, dass Traefik eingehende `X-Forwarded-*`-Köpfe fremder Quellen verwirft
(`forwardedHeaders.trustedIPs` am Entrypoint nicht auf beliebige Quellen gesetzt, kein
`forwardedHeaders.insecure`). Sonst setzt ein Angreifer den Kopf selbst und umgeht die
Zählbremse. Solange das nicht geprüft ist, `FBCRM_TRUSTED_PROXIES` leer lassen: Dann zählen alle
Anfragen unter Traefiks Adresse — strenger als nötig, aber nicht umgehbar.
