# Releasing

fb.crm trägt eine dreiteilige Betriebsversion **X.Y.Z**, gepflegt in [`VERSION`](VERSION)
(Quelle der Wahrheit) und von dort in `pom.xml` sowie `frontend/package.json`/
`package-lock.json` synchronisiert. Jede Erhöhung setzt die niedrigeren Teile auf `0` zurück
(Z zählt „Pushes seit dem letzten Production-Release", Y „Production-Releases seit dem
letzten Major").

Dieses Dokument wird von den Skills `push-main` und `merge-production` gelesen — sie führen
die unten genannten Schritte automatisch als Teil des jeweiligen Triggers aus.

## push main

Bei jedem `push main`: Patch-Teil erhöhen (Z+1).

```
node scripts/bump-version.mjs patch
```

## merge production

Bei jedem `merge production`: Minor-Teil erhöhen (Y+1, Z→0), Changelog schreiben und den
Release taggen. Schrittfolge:

```
node scripts/bump-version.mjs minor # VERSION/pom/package auf die neue Version setzen
node scripts/gen-changelog.mjs      # Changelog-Block der NEUEN Version oben in CHANGELOG.md
# Release-Commit (VERSION, pom.xml, package(-lock).json, CHANGELOG.md)
node scripts/bump-version.mjs tag   # annotated Tag vX.Y.Z auf den Release-Commit setzen
git push origin main --follow-tags  # main + Tag pushen (annotated Tag wird mitgenommen)
# PR main -> production erstellen (Mannes Merge ist der Stop-Punkt)
# nach dem Merge: GitHub Release zum Tag vX.Y.Z anlegen (Changelog-Block als Beschreibung)
```

Reihenfolge beachten: **erst** der Version-Bump, **dann** `gen-changelog.mjs`, **dann** der
Release-Commit, **erst danach** `bump-version.mjs tag` — das Skript liest die Zielversion aus
`VERSION` für den Blocktitel; liefe `gen-changelog.mjs` vor dem Bump, entstünde ein Block für die
alte Version. Der Tag muss nach dem Release-Commit gesetzt werden, sonst zeigt er auf den Commit
davor statt auf den eigentlichen Release-Stand.

`gen-changelog.mjs` grenzt den Range über den Tag der Vorversion ab (roher Dump der Commit-Titel,
Keep-a-Changelog-Format). `bump-version.mjs tag` setzt den annotated Tag `vX.Y.Z` (annotated statt
lightweight, damit `git push --follow-tags` ihn mitnimmt — kein separater Tag-Push nötig), der beim
nächsten Release wiederum die Range-Untergrenze bildet. Ein `push main` (Patch-Bump) erzeugt
bewusst weder Changelog-Block noch Tag.

## rollback production

Der Rückweg einer ausgelieferten Version auf die Version davor. Zurück heißt immer: Marke
auschecken **und** die Sicherung von vor dem Update einspielen. Flyway migriert nur vorwärts —
eine ältere Version bricht beim Start an einem neueren Schema mit unbekannter Migration ab.
Die Einzelbefehle stehen in [`docs/betrieb.md`](docs/betrieb.md), Abschnitt „Wiederherstellung";
hier steht nur die Reihenfolge:

1. **Zielversion bestimmen** — die Marke `vX.Y.Z` der Version vor dem Update, vermerkt in
   `sicherung/version.txt` bzw. in „Aktualisierung", Schritt 1.
2. **Anwendung anhalten:**
   ```
   docker compose stop caddy fbcrm-api
   ```
3. **Marke auschecken:**
   ```
   git checkout vX.Y.Z
   ```
4. **Sicherung von vor dem Update einspielen** nach „Wiederherstellung", Schritte 2 bis 6. Auf
   demselben Rechner tragen die Bänder `postgres_data` und `minio_data` noch das neuere Schema;
   sie werden vor Schritt 4 dort mit `docker compose down -v` entfernt — erst, wenn die Sicherung
   geprüft vorliegt, denn danach ist sie der einzige Datenstand.
5. **Neu bauen und starten:**
   ```
   docker compose up -d --build
   ```
   Hinter Traefik mit `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build`.
6. **Prüfen** nach „Wiederherstellung", Schritt 8.

## Major-Version erhöhen

**Nur auf Mannes explizite Anordnung** — er tippt im Chat genau die Phrase
„Major-Version erhöhen". Kein automatischer Trigger, nicht Teil von `push main` oder
`merge production`.

```
node scripts/bump-version.mjs major
```

Erhöht den Major-Teil (X+1, Y→0, Z→0).
