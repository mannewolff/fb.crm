# CLAUDE-design.md — Designsprache von fb.crm: Kupferwolke

Diese Datei ist die Designquelle der Anwendung **fb.crm**. Sie beschreibt, **was** die Oberfläche trägt: Vorlage, Erscheinungsbild, Palette, Schrift, Radien, Tiefe, Kontrast, Rahmen und Zustandsformen. **Wie** diese Werte im Code angewendet werden — Theme-zentral, über die `sx`-Prop, keine hartcodierten Werte — regelt [CLAUDE-react.md](CLAUDE-react.md).

**Geltungsbereich:** ausschließlich diese Anwendung. Regeln für Veröffentlichungen (Blog, LinkedIn, Whitepaper, Website) und für Präsentationen gelten hier **nicht**. Auch die Gestaltung der erzeugten PDF-Dokumente (Angebot, Rechnung, Leistungsnachweis) fällt nicht hierunter — sie folgt Kapitel 06 der Spezifikation; für die **Rechnung** gilt an dessen Stelle die verbindliche Vorlage [`docs/vorlage-rechnung.pdf`](docs/vorlage-rechnung.pdf).

---

## 🧭 Leitgedanke

**Weich, warm, luftig — und trotzdem ein Arbeitswerkzeug.** Die Oberfläche soll sich anfühlen wie ein freundliches Notizbuch über die eigenen Kunden, nicht wie ein Schaltpult. Daraus folgen drei Regeln, die über allen Einzelwerten stehen:

1. **Karten statt Linien.** Gliederung entsteht durch Abstand, Fläche und weiche Schatten — Haarlinien nur, wo Zeilen sonst ineinanderlaufen.
2. **Eine Hauptsache je Ansicht.** Es gibt genau eine Kupfertaste; alles andere tritt zurück. Seltene und folgenreiche Aktionen stehen nicht gleichrangig daneben.
3. **Zeigen, wie es steht.** Eine Ansicht erzählt zuerst den Zustand (Kürzel, Chips, Kennzahlen, jüngste Ereignisse), dann die Stammdaten.

---

## 📌 Vorlage und Abnahme

**Die Vorlage ist verbindlich:** [`docs/entwurf-kupferwolke.html`](docs/entwurf-kupferwolke.html) — „Kupferwolke". Sie gilt für **Aussehen und Aufbau**: Farben, Schrift, Radien, Tiefe, Rahmen, Zustandsformen und die Gestalt der Bausteine (Schiene, Kopf, Bühne, Karte, Innenkarte, Kopfkarte, Kachel, Taste, Icontaste, Chip, Mal, Zeitleiste).

**Sie gilt ausdrücklich nicht für Inhalte und Ansichten.** Die Vorlage zeigt eine Firmen-Detailansicht mit **erfundenen Beispielinhalten** — Navigationseinträge, Kennzahlen, Zeitleiste, Glocke und Telefonnummer sind Platzhalter. Welche Ansichten fb.crm hat, was darauf steht und unter welcher Route sie liegen, entscheiden die Fachpläne. Aus der Vorlage wird **nichts Fachliches** übernommen.

**Bindend sind** die Vorlage, diese Datei und `frontend/src/theme.ts` als einzige Wertequelle im Code. Weichen Vorlage und diese Datei voneinander ab, **gilt diese Datei**. Ein Plan oder ein Arbeitspaket entscheidet keine Gestaltungsfrage gegen die Vorlage — eine Abweichung wird Manne vorgelegt.

**Abgenommen wird visuell:** je Ansicht ein Bildschirmfoto bei 1440 × 900 neben der Vorlage. Tests und Gates sichern Werte, Kontrast und Verhalten; ob eine Ansicht aussieht wie die Vorlage, sagen sie nicht.

---

## ☀️ Erscheinungsbild

**Ein Erscheinungsbild: hell — unabhängig von der Einstellung des Rechners oder Browsers** (Entscheidung Manne, 2026-09-22). Es gibt keinen Schalter, keine Umschaltung über `prefers-color-scheme`, keinen Zustand und keine Persistenz. `theme.ts` kennt nur das helle Farbschema; `theme.test.ts` hält fest, dass kein weiteres dazukommt und kein CSS unter `prefers-color-scheme` entsteht.

**Tokens sind Verweise, keine Werte.** Die Konstanten aus `theme.ts` tragen `var(--fb-…)`. In den Ansichten gilt `theme.vars.palette.*`, nicht `theme.palette.*`.

---

## 🎨 Palette

**Die Leitfarbe ist Kupfer.** Die Werte unten sind bereits auf WCAG AA gerechnet (siehe [Kontrast](#kontrast)); die Vorlage trägt dieselben Werte.

### Flächen, Linien, Text

| Rolle | Wert | Verwendung |
|---|---|---|
| Grund | `#F6F3EF` | Grund der Anwendung (`background.default`), warmes Cremeweiß |
| Fläche | `#FFFFFF` | Karten, Schiene, Suche, Menüs (`background.paper`) |
| Fläche weich | `#FBF9F6` | Innenkarten, weiche Tasten, Icontasten, Zähler, Hover-Grund von Zeilen |
| Linie | `#EFE9E3` | seltene Haarlinien, gestrichelter Rand der Hinzufügen-Kachel (`divider`) — rein gliedernd, trägt nie eine Aussage |
| Rand stark | `#928577` | Rand von Eingabefeldern und allem, dessen Umriss man erkennen muss (≥ 3:1) |
| Text | `#1F1B18` | Fließtext, Titel (`text.primary`) |
| Text matt | `#6B625B` | Sekundärtext, Navigation (`text.secondary`) |
| Text schwach | `#756C64` | Gruppentitel, Pfad, Hinweise, Beschriftungen in Stammdaten |

### Kupfer

| Rolle | Wert | Verwendung |
|---|---|---|
| Kupfer | `#A0521F` | Leitfarbe (`primary`): Links, Fokusring, kupferne Schrift, aktive Zustände |
| Kupfer Taste | `#AE5A24` → `#8E4718` | Verlauf (135°) der Kupfertaste, weiße Schrift darauf |
| Kupfer-Glanz | `#E08A4F` | **nur Schmuck:** Verlauf des Markenmals, Hover-Rand der Hinzufügen-Kachel — nie Schrift, nie Grund von Schrift |
| Kupfer-Schatten | `rgba(184,97,42,.55)` | farbiger Schatten der Kupfertaste und des Markenmals |

### Tönungen

Pastellflächen mit ihrer Schrift. Sie tragen **Kategorien und Zustände**, jede Tönung hat eine feste Bedeutung — sie werden nicht der Reihe nach „durchgefärbt".

| Tönung | Fläche | Schrift | Bedeutung |
|---|---|---|---|
| Pfirsich | `#FDEBDD` | `#8A4418` | Kupfer-Familie: aktiver Navigationseintrag, Firmen-Mal, Hover der Icontaste, bestellte Angebote (laufende Arbeit) |
| Salbei | `#E4F1E8` | `#2E6B45` | erfolgreich, aktiv, bezahlt, Umsatz |
| Himmel | `#E3EFFB` | `#1F5A96` | Information, laufend, versendet |
| Bernstein | `#FBF0D9` | `#7A5510` | Warnung, Grenze erreicht, bald fällig |
| Rosé | `#FBE4E4` | `#A12D31` | gescheitert, überfällig, stillgelegt |
| Flieder | `#ECEAFB` | `#4B3FA0` | neutrale Kategorie: Personen, Ansprechpartner, Rechnungen als Menge |

### Melder

Kräftige Zustandsfarben für Symbole, Zahlen und schmale Markierungen. Sie stehen **auf Flächen (Karten)**, nie direkt auf dem Grund.

| Melder | Wert | Bedeutung |
|---|---|---|
| Grün | `#277A42` | erfolgreich, fertig |
| Bernstein | `#8F6410` | Warnung, Grenze erreicht |
| Zinnober | `#C8393E` | gescheitert, überfällig |
| Stahl | `#2F6FC9` | laufend, Information |
| Grau | `#6B737F` | nicht bearbeitet |

**Grund der Anwendung:** das Cremeweiß mit zwei weichen Schimmern — Kupfer-Glanz oben links (`radial-gradient(900px 500px at 12% -10%, rgba(224,138,79,.18), transparent 60%)`) und Flieder oben rechts (`radial-gradient(700px 500px at 100% 0%, rgba(160,150,240,.12), transparent 60%)`).

---

## ✒️ Typografie

**Eine Schrift: Plus Jakarta Sans** — rund, offen, freundlich. Keine zweite Anzeige- und keine Monoschrift.

| Rolle | Größe / Gewicht | Einsatz |
|---|---|---|
| Ansichtstitel | 28 px / 800, Laufweite −0,02 em | Name in der Kopfkarte |
| Kennzahl | 26 px / 800, Laufweite −0,02 em | Zahl in einer Kachel |
| Kartentitel | 18 px / 700 | Überschrift einer Karte |
| Markenname | 17 px / 800 | „fb.crm" in der Schiene |
| Name in Liste | 15 px / 700 | Person, Firma, Vorgang in Karten und Zeilen |
| Fließtext | 14,5 px / 400, Zeilenhöhe 1,5 | alles Lesbare |
| Navigation, Tasten | 14 px / 500 bzw. 600; aktiver Eintrag 700 | Schiene, Tasten |
| Klein | 12–13,5 px / 500–600 | Chips, Gruppentitel, Zähler, Zeitangaben |

- **Satzschreibung überall** — auch Gruppentitel der Schiene („Stammdaten", nicht „STAMMDATEN"). Keine Versalien mit Laufweite.
- **Zahlen und Kennungen** (Beträge, Mengen, Datumsangaben, Nummern wie `R-2026-006`) tragen `font-variant-numeric: tabular-nums`, wo sie untereinander stehen. Plus Jakarta Sans führt Tabellenziffern (`tnum`).
- **Eine große Einzelzahl** (Kachel) braucht keine Tabellenziffern.
- Die Schrift wird **offline mit der Anwendung ausgeliefert** (`@fontsource`); eine Instanz ohne Internetzugang zeigt dasselbe Schriftbild. Der Google-Fonts-Link in der Vorlage gilt nur für die Vorlage.

---

## 📐 Radien

| Ebene | Radius | Einsatz |
|---|---|---|
| Groß | 28 px | Karte, Kopfkarte, Schiene |
| Kachel | 24 px | Kennzahl-Kachel, Fuß der Schiene |
| Mittel | 22 px | Innenkarte (Person in einer Karte), Hinzufügen-Kachel |
| Mal | 26 px (84 px Mal) bzw. 14–16 px (Symbolfelder 36–48 px) | Firmen-Mal, Markenmal, Symbolfeld in Kachel und Zeitleiste |
| Klein | 14 px | Navigationseintrag, Zeile, Eingabefeld |
| Rund | 999 px | Tasten, Chips, Suche, Zähler; Kreis für Personen-Kürzel und Icontasten |

Der Fokusring folgt dem Radius des Elements.

---

## 🌓 Tiefe

**Drei Stufen: Grund < Karte < Abgehoben.** Karten schweben auf dem Grund; was man gerade berührt oder was über allem liegt (Innenkarte im Hover, Menü, Dialog), hebt sich weiter ab. **Es gibt keine eingelassenen Flächen mehr** — Suche und Eingabefelder sind helle Flächen, keine Rillen. Innenkarten liegen als „Fläche weich" ohne eigenen Schatten in der Karte.

| Token | Wert | Rolle |
|---|---|---|
| `schatten-karte` | `0 1px 2px rgba(80,50,30,.04), 0 8px 24px -6px rgba(80,50,30,.10)` | Karte, Schiene, Suche, runde Kopftasten |
| `schatten-hoch` | `0 2px 4px rgba(80,50,30,.05), 0 18px 40px -10px rgba(80,50,30,.18)` | Innenkarte im Hover, Menü, Dialog |
| `schatten-kupfer` | `0 8px 20px -6px` Kupfer-Schatten | Kupfertaste, Markenmal |

Die Schattenfarbe ist ein warmes Braun `rgba(80,50,30,…)` — kein Blaugrau, kein Schwarz.

---

## ♿ Kontrast

**WCAG AA ist das Mindestmaß: 4,5:1 für Text**, 3:1 für großen Text und für Umrisse und bedeutungstragende Grafik (Eingabefeldrand, Fokusring, Melder-Symbole). Gerechnet wird gegen die Fläche, auf der das Element tatsächlich steht, mit dem Kontrastrechner im Frontend (`lib/contrast.ts`); die Tabelle steht im Test des Themes. **Die Schwelle wird nie gesenkt** — reicht ein Ton nicht, wird er im selben Farbton vertieft und hier nachgetragen.

Nachgerechnet am 2026-09-26 (Auszug, schwächste Paarung je Rolle):

| Paarung | Verhältnis |
|---|---|
| Text schwach auf Grund | 4,65:1 |
| Text matt auf Grund | 5,39:1 |
| Kupfer auf Grund | 5,10:1 · auf Pfirsich 4,86:1 |
| Weiß auf Kupfer Taste (helles Ende `#AE5A24`) | 4,90:1 |
| Tönungsschrift auf ihrer Fläche | Pfirsich 6,20 · Salbei 5,46 · Himmel 6,08 · Bernstein 5,92 · Rosé 5,90 · Flieder 7,04 |
| Melder auf Fläche (Weiß) | Grün 5,32 · Bernstein 5,25 · Zinnober 5,11 · Stahl 4,96 · Grau 4,79 |
| Rand stark auf Fläche weich | 3,42:1 |

**Bekannte Grenzen:** Kupfer-Glanz (2,65:1 gegen Weiß) und Linie (1,20:1) erreichen keine Schwelle und dürfen deshalb **nie** Schrift oder eine Aussage tragen. Stahl (4,48:1) und Grau (4,33:1) verfehlen AA auf dem Grund — deshalb stehen Melder nur auf Flächen.

---

## 🧱 Rahmen

Vorlage: `docs/entwurf-kupferwolke.html`.

- **Aufbau:** zweispaltig mit 20 px Außenabstand und 24 px Spalt; links die Schiene (260 px), rechts die Bühne mit höchstens 1180 px Breite.
- **Schiene:** eine freistehende weiße Karte (Radius groß, `schatten-karte`), klebt oben. Oben die **Marke**: Markenmal (42 px, Radius 14, Verlauf Kupfer-Glanz → Kupfer, Kupfer-Schatten), Name, Version. Darunter **Navigationsgruppen**, jede mit einem Gruppentitel in Satzschreibung und immer offen. Ein Eintrag ist ein echter Link mit Symbol (20 px) und Beschriftung, Radius klein; Hover „Fläche weich". Der **aktive Eintrag** (`aria-current="page"`, längster passender Pfad) liegt auf Pfirsich mit Pfirsich-Schrift in 700. Zähler an Einträgen als runde Plakette rechts — erst, wenn die Shell diese Daten kennt. Unten der **Fuß** als Kachel „Fläche weich": Personen-Kürzel, Name, Rolle und der Weg zu den Einstellungen; ein Klick öffnet das Menü mit „Profil bearbeiten" und „Abmelden". Welche Gruppen und Einträge es gibt, entsteht mit den Fachplänen. Eingeklappt (76 px) bleiben Markenmal, Symbole und Kürzel.
- **Die Schiene ist der einzige Ort, an dem man zwischen den Ansichten wechselt.**
- **Kopf:** eine Zeile ohne eigene Fläche über der Bühne. Links der **Pfad** (Text schwach, verlinkte Stufen, Chevron als Trenner, letzte Stufe in Text und 700). Rechts die **Suche** als weiße Pille (320 px, `schatten-karte`) mit Lupe, Platzhalter und der Tastenkappe des echten Kürzels `/`; daneben runde Kopftasten (42 px) für Dinge, die es geben wird — keine ohne fachlichen Anlass.
- **Bühne:** Abstand zwischen Bereichen 22 px, Innenabstand einer Karte 28 px.
- **Fokusring:** 2 px Kupfer mit 2 px Abstand an jedem Tastaturziel; Eingabefelder zeigen den Fokus als 2 px Kupferrand.
- **Mindestbreite:** 768 px vollständig bedienbar; unterhalb von 900 px liegt die Schiene hinter einer Schaltfläche.

### Bausteine

- **Kopfkarte** (Detailansichten): Mal (84 px, Radius 26, Tönung mit Kürzel in 800), Titel, eine Zeile mit den wichtigsten Stammdaten (Symbol davor, Teile durch „·" getrennt), darunter Chips. Rechts die Aktionen. Ein weicher Pfirsich-Kreis rechts oben ist Schmuck.
- **Kennzahl-Kacheln:** Raster aus zwei bis vier Kacheln, jede auf einer Tönung mit Symbolfeld (48 px, halbtransparentes Weiß), Beschriftung (13 px / 600) und Zahl.
- **Innenkarten:** für **wenige gleichrangige Objekte** innerhalb einer Karte (z. B. Ansprechpartner einer Firma) — Raster, Personen-Kürzel (48 px Kreis auf Tönung), Name, Rolle, Kontaktwege mit Symbol. Die letzte Kachel ist die **Hinzufügen-Kachel** (gestrichelter Rand).
- **Zeilen:** **Listen mit vielen Einträgen** (Firmenliste, Vorgänge, Rechnungen) sind Zeilen in einer Karte, kein Kartenraster. Zeile mit Radius klein, Hover „Fläche weich", ganze Zeile als Link auf das Objekt.
- **Zeitleiste:** Einträge mit Symbolfeld (36 px, Radius 12, Tönung nach Art des Ereignisses), Titel (600) und Unterzeile (Text schwach).
- **Stammdaten:** wenn sie ausführlich stehen müssen, als Beschriftung–Wert-Liste ohne Linien je Zeile, Beschriftung in Text schwach.

### Tasten

- **Kupfertaste:** Pille, Verlauf Kupfer Taste, weiße Schrift 600, `schatten-kupfer`. **Genau eine je Ansicht** — die Hauptaktion. Sie steht in der Kopfkarte (Detailansicht) bzw. rechts im Kartenkopf der Liste.
- **Weiche Taste:** Pille, „Fläche weich" mit 1 px Linie als Innenring, Textfarbe — für die zweitwichtigste Aktion (z. B. „Bearbeiten").
- **Icontaste:** Kreis 40 px, „Fläche weich", Symbol in Text matt; Hover Pfirsich. Braucht immer einen zugänglichen Namen.
- **⋯-Menü:** **Seltene und folgenreiche Aktionen** (Stilllegen, Löschen, Archivieren) stehen nie als gleichrangige Taste neben den anderen, sondern im ⋯-Menü; im Menü tragen sie Rosé-Schrift und fragen vor der Ausführung nach.
- **Hover:** Tasten heben sich um 1 px; Innenkarten gehen im Hover auf Weiß mit `schatten-hoch`.
- **Aktionen, die erst im Hover erscheinen** (⋯ an Innenkarten), erscheinen ebenso bei **Tastaturfokus** (`:focus-within`) und sind auf Geräten ohne Hover (`@media (hover: none)`) **immer sichtbar**.

### Eingabefelder

Weiße Fläche, 1 px „Rand stark", Radius klein, Beschriftung über dem Feld (13 px / 600, Text matt). Fokus: 2 px Kupferrand. Fehler: Rand und Hinweistext in Zinnober, dazu ein Symbol — nie Farbe allein.

---

## 🧭 Zustandsformen

Zustände sind an **Form** erkennbar, nicht allein an Farbe — ein **Chip mit Symbol und Wort**, eine Tönung mit Symbolfeld, ein Kürzel. Farbe allein trägt nie eine Aussage.

- **Chip:** Pille, 12,5 px / 600, Symbol links, Tönung nach Bedeutung (Tabelle „Tönungen"). Das Wort sagt den Zustand („Aktiv", „Überfällig"), das Symbol stützt ihn.
- **Wähler in der Werkzeugleiste:** **keine Beschriftung über dem Feld**. Die Benennung trägt der Wert selbst — „‹Merkmal›: alle", „‹Merkmal›: ‹Wert›". Form wie eine weiche Taste, damit die Leiste in einer Linie steht. Den **zugänglichen Namen** behält das Feld: Ohne sichtbare Beschriftung ist er der einzige.
- **Leere Zustände:** eine Einladung, keine Entschuldigung — Symbol auf Tönung, ein Satz, eine Taste („Ersten Ansprechpartner anlegen").
- **Bewegung reduzieren:** eine zentrale Regel setzt unter `prefers-reduced-motion: reduce` Übergangs- und Animationsdauern auf null, auch das Anheben im Hover.
- **Ausdruck:** schlicht und immer hell — ohne Grund, Schimmer, Verläufe und Schatten.

---

## 🔢 Weitere Tokens

**Einzige Wertequelle im Code ist `frontend/src/theme.ts`**, gespeist aus dieser Datei und der Vorlage: Palette, Tönungen, Melder, Schatten, Radien, Grund, Schrift, Tabellenziffern. Abgeleitete Farbzuordnungen (etwa Status → Tönung) liegen in eigenen Modulen unter `frontend/src/lib/`. Diese Datei nennt keine Tokennamen im Code — die legt der Umsetzungsplan fest.

---

## 📜 Historie

- **seit 2026-09-26 „Kupferwolke":** löst die Kupferwarte ab (Entscheidung Manne, 2026-09-26: „zu sehr 60er"). Weicher, warmer Stil mit schwebenden Karten, großen Radien, Pastell-Tönungen und Plus Jakarta Sans; Kupfer bleibt Leitfarbe. Vorlage `docs/entwurf-kupferwolke.html`.
- **2026-09-26 abgeschlossen:** Mit Issue #83 trägt der Code durchgehend die Kupferwolke — `theme.ts` und alle Bausteine führen ihre Werte und Namen, die Vorlage der Kupferwarte ist aus dem Repository entfernt. Der Abschnitt „Übergang" ist damit entfallen.
- **2026-09-17 bis 2026-09-26 „Kupferwarte":** Leitstand-Stil aus dem Repo kanban-kit (Vorlage `docs/entwurf-leitstand.html`) — Nut, Platte, Taste, LED; Archivo und IBM Plex.
- **Die Lehre für jeden Gestaltungswechsel:** Zuerst wird **diese Datei** umgestellt, dann geplant und umgesetzt — ein Plan folgt der Quelle, die im Repository als bindend markiert ist.
