# CLAUDE-design.md — Designsprache von fb.crm: Kupferwarte

Diese Datei ist die Designquelle der Anwendung **fb.crm**. Sie beschreibt, **was** die Oberfläche trägt: Vorlage, Erscheinungsbild, Palette, Schrift, Radien, Tiefe, Kontrast, Rahmen und Zustandsformen. **Wie** diese Werte im Code angewendet werden — Theme-zentral, über die `sx`-Prop, keine hartcodierten Werte — regelt [CLAUDE-react.md](CLAUDE-react.md).

**Geltungsbereich:** ausschließlich diese Anwendung. Regeln für Veröffentlichungen (Blog, LinkedIn, Whitepaper, Website) und für Präsentationen gelten hier **nicht**. Auch die Gestaltung der erzeugten PDF-Dokumente (Angebot, Rechnung, Leistungsnachweis) fällt nicht hierunter — sie folgt Kapitel 06 der Spezifikation.

---

## 📌 Vorlage und Abnahme

**Die Vorlage ist verbindlich:** [`docs/entwurf-leitstand.html`](docs/entwurf-leitstand.html) — „Kupferwarte". Sie gilt für **Aussehen und Aufbau**: Farben, Schriften, Radien, Tiefe, Rahmen, Zustandsformen und die Gestalt der Bausteine (Schiene, Kopf, Bühne, Platte, Nut, Taste, Wähler, Etikett, LED).

**Sie gilt ausdrücklich nicht für Inhalte und Ansichten.** Die Vorlage stammt aus einem anderen Produkt und führt dessen Ansichten vor; welche Ansichten fb.crm hat, was darauf steht und unter welcher Route sie liegen, entscheiden die Fachpläne. Aus der Vorlage wird **nichts Fachliches** übernommen — weder Bezeichnungen noch Kennzahlen noch Bedienelemente, für die es in fb.crm keinen Anlass gibt.

**Bindend sind** die Vorlage, diese Datei und `frontend/src/theme.ts` als einzige Wertequelle im Code. Ein Plan oder ein Arbeitspaket entscheidet keine Gestaltungsfrage gegen die Vorlage — eine Abweichung wird Manne vorgelegt.

**Abgenommen wird visuell:** je Ansicht ein Bildschirmfoto bei 1440 × 900 neben der Vorlage. Tests und Gates sichern Werte, Kontrast und Verhalten; ob eine Ansicht aussieht wie die Vorlage, sagen sie nicht.

---

## ☀️ Erscheinungsbild

**Ein Erscheinungsbild: hell — unabhängig von der Einstellung des Rechners oder Browsers** (Entscheidung Manne, 2026-09-22; wie im KI-Leitstand, aus dem diese Datei stammt). Es gibt keinen Schalter, keine Umschaltung über `prefers-color-scheme`, keinen Zustand und keine Persistenz. `theme.ts` kennt nur das helle Farbschema; `theme.test.ts` hält fest, dass kein weiteres dazukommt und kein CSS unter `prefers-color-scheme` entsteht.

**Die Vorlage trägt einen Dunkelsatz, der nicht gilt.** `docs/entwurf-leitstand.html` führt neben den hellen Werten an `:root` auch dunkle unter `prefers-color-scheme: dark` und einen Umschalter `data-theme`. Beides übernimmt die Anwendung nicht; es gelten allein die hellen Werte.

**Tokens sind Verweise, keine Werte.** Die Konstanten aus `theme.ts` tragen `var(--fb-…)`. In den Ansichten gilt `theme.vars.palette.*`, nicht `theme.palette.*`.

---

## 🎨 Palette

Die Rollen und Werte der Vorlage (Entwurf Z. 10–150). Die **Leitfarbe ist Kupfer**; die Melder tragen ausschließlich Zustände und werden nie als Akzent verwendet.

| Rolle | Wert | Verwendung |
|---|---|---|
| Grund | `#E7E9ED` | Grund der Anwendung (`background.default`) |
| Grund tief | `#D8DBE2` | oberes Ende der Schiene |
| Nut | `#D5D9E0` | eingelassene Flächen: Schiene, Suche, Filtergruppen, Zähler |
| Platte | `#FDFDFE` | Inhaltsflächen: Karten, Kacheln, Platten (`background.paper`) |
| Platte Fuß | `#F2F4F7` | unteres Ende eines Tastenverlaufs |
| Platte hoch | `#FFFFFF` | abgehobene Flächen, oberes Ende eines Tastenverlaufs |
| Rand | `#CDD2DA` | Haarlinien (`divider`) |
| Rand stark | `#B7BEC9` | betonte Linien, Tastenkappen |
| Kante | `rgba(255,255,255,.9)` | Lichtkante an der Oberkante erhabener Flächen |
| Text | `#14181E` | Fließtext (`text.primary`) |
| Text matt | `#58606C` | Sekundärtext (`text.secondary`), Navigation |
| Text schwach | `#868E9B` | Etiketten, Zähler, Hinweise — siehe [Kontrast](#kontrast) |
| Kupfer | `#A85F2C` | Leitfarbe (`primary`): aktive Navigation, Primärtaste, Fokusring, Füllungen |
| Kupfer hell | `#C2743C` | oberes Ende von Kupferverläufen |
| Kupfer-Schimmer | `rgba(168,95,44,.16)` | Schimmer im Grund, Schatten der Kupfertaste |
| Grün | `#2F8F4E` | Melder: erfolgreich, fertig |
| Bernstein | `#B07C15` | Melder: Warnung, Grenze erreicht |
| Zinnober | `#C8393E` | Melder: gescheitert, überfällig |
| Stahl | `#2F6FC9` | Melder: laufend, Information |
| Grau | `#8A929E` | Melder: nicht bearbeitet |

**Grund der Anwendung:** der Grund mit einem Kupfer-Schimmer oben links (`radial-gradient(1100px 600px at 18% -8%, Kupfer-Schimmer, transparent 62%)`, Entwurf Z. 152–160).

---

## ✒️ Typografie

| Rolle | Schrift | Einsatz |
|---|---|---|
| Titel, Anzeige | **Archivo** (variable Breite, `font-stretch` 110–118 %) | Überschriften, Markenname, Pfad, Etiketten, Plattentitel |
| Fließtext | **IBM Plex Sans**, 14 px, Zeilenhöhe 1,5 | alles Lesbare |
| Zahlen und Kennungen | **IBM Plex Mono** mit `tabular-nums` | Nummern, Beträge, Mengen, Datumsangaben, Tastenkürzel, Kennzahlen |

- **Gewichte wie in der Vorlage:** 400, 500 (Navigation, Titel in Listen), 600 (Tasten, Etiketten, Pfad, Plattentitel), 700 (Markenname, große Zahlen).
- **Etikett** (`.etikett`, Entwurf Z. 185–194): Archivo, 10 px, 600, Versalien, Laufweite 0,14 em, Farbe „Text schwach".
- Alle Schriften werden **offline mit der Anwendung ausgeliefert** (`@fontsource`); eine Instanz ohne Internetzugang zeigt dasselbe Schriftbild.
- **Eine große Einzelzahl** trägt keine Tabellenziffern, Zahlen untereinander immer.

---

## 📐 Radien

| Ebene | Radius | Token |
|---|---|---|
| Platte, Kachel, Laufband | 14 px | `PANEL_RADIUS` (`--r-gross`) |
| Karte, Navigationseintrag | 10 px | `CARD_RADIUS` (`--r-mittel`) |
| Bedienelement, Fokusring | 6 px | `shape.borderRadius` (`--r-klein`) |

---

## 🌓 Tiefe

**Vier Stufen: Nut < Grund < Platte < Abgehoben** (Entwurf Z. 6–8). Eingelassenes liegt als Nut im Grund (Schiene, Suche, Filtergruppen, Zähler); Inhalte liegen als Platte darauf; was gerade bewegt wird oder über allem schwebt, ist abgehoben. Tasten sind kleine erhabene Flächen mit Lichtkante, die beim Drücken zur Nut werden.

| Token | Rolle |
|---|---|
| `--schatten-nute` | Innenschatten eingelassener Flächen |
| `--schatten-platte` | Lichtkante plus zwei Schattenebenen der Platte |
| `--schatten-hoch` | abgehobene Fläche, weiter geöffnet |
| `--schatten-taste` | Lichtkante plus kurzer Schatten einer Taste |

Die Schattenfarbe folgt der Vorlage: eine Blaugrau-Tinte `rgba(18,24,33,…)`.

---

## ♿ Kontrast

**WCAG AA ist das Mindestmaß: 4,5:1 für Text**, 3:1 für großen Text und bedeutungstragende Grafikelemente. Gerechnet wird gegen die Fläche, auf der das Element tatsächlich steht, mit einem Kontrastrechner im Frontend; die Tabelle steht im Test des Themes.

**Die Vorlage verfehlt AA an wenigen Stellen.** Dort wird der Ton **minimal im selben Farbton** vertieft, bis die Schwelle hält; die Abweichung steht an der Konstante in `theme.ts` und im Test — die Schwelle wird nie gesenkt. Die bekannten Stellen:

- **Text schwach als Schrift:** 2,3–3,3:1 (auf Nut, Grund, Platte).
- **Text matt auf der Nut:** 4,49:1.
- **Melder auf der Nut:** Grün 2,9, Bernstein 2,6, Grau 2,2:1 — als Füllung auf der Nut vertieft oder auf eine Platte gesetzt.

---

## 🧱 Rahmen

Vorlage: Entwurf CSS Z. 196–389, HTML Z. 1101–1199.

- **Warte:** zweispaltig, links die Schiene (224 px), rechts der Inhalt.
- **Schiene:** eingelassene Nut mit Verlauf von „Grund tief" nach „Nut", Innenschatten, Haarlinie rechts. Oben die **Marke** (Kupfer-Mal mit drei Balken, Name, Version); darunter **Navigationsblöcke**, jeder mit einem Etikett als Titel und immer offen; unten der **Fuß** mit den seltener gebrauchten Wegen und dem Einklappen. Welche Blöcke es gibt und welche Einträge darin stehen, entsteht mit den Fachplänen und wird dort festgehalten — diese Datei regelt nur die Gestalt. Ein Eintrag ist ein echter Link mit Symbol und Beschriftung; der aktive Eintrag (`aria-current="page"`, der längste passende Pfad) ist eine erhabene Taste mit kupfernem Symbol. Zahlen an Einträgen erscheinen erst, wenn die Shell diese Daten kennt. Eingeklappt (64 px) bleiben nur die Symbole.
- **Die Schiene ist der einzige Ort, an dem man zwischen den Ansichten wechselt.** Die Reiterleiste, die die Vorlage oben zum Wechseln zeigt, übernimmt die Anwendung nicht.
- **Kopf:** klebt oben, leicht getönt mit Weichzeichner; **Pfad** in Archivo mit verlinkten Stufen, **Suche** als Nut mit der Tastenkappe des echten Kürzels `/` (die Vorlage zeigt `⌘K`; Modifikator-Kürzel überlässt die Anwendung dem Browser), **Nutzer** als rundes Mal mit Kürzel, das ein Menü mit „Profil bearbeiten" und „Abmelden" öffnet. Die Kupfertaste für die Hauptaktion der Ansicht bringt das jeweilige Ansichtspaket mit.
- **Bühne:** Innenabstand 22/26/44 px, Abstand zwischen Bereichen 20 px.
- **Fokusring:** 2 px Kupfer mit 2 px Abstand an jedem Tastaturziel; Eingabefelder zeigen den Fokus an ihrer Rahmenlinie.
- **Mindestbreite:** 768 px vollständig bedienbar; unterhalb von 900 px liegt die Schiene hinter einer Schaltfläche.

---

## 🧭 Zustandsformen

Zustände sind an **Form** erkennbar, nicht allein an Farbe — eine Plakette mit LED, eine Vertiefung, eine Lichtkante, ein Symbol. Farbe allein trägt nie eine Aussage.

- **Wähler in der Werkzeugleiste** (`.waehler`, CSS Z. 833–842; HTML Z. 1687–1690): **keine Beschriftung über dem Feld**. Die Benennung trägt der Wert selbst — „‹Merkmal›: alle", „‹Merkmal›: ‹Wert›" —, sichtbar wie in der Vorlage, damit auch ein gewählter Wert sagt, wonach gefiltert wird. Maße wie die Filtertasten daneben (Schrift 11,5 px, Innenabstand 4/9 px, Radius 7 px), damit die Leiste in einer Linie steht. Den **zugänglichen Namen** behält das Feld: Ohne sichtbare Beschriftung ist er der einzige.
- **Bewegung reduzieren:** eine zentrale Regel setzt unter `prefers-reduced-motion: reduce` Übergangs- und Animationsdauern auf null, auch das Pulsieren der LED.
- **Ausdruck:** schlicht und immer hell — ohne Grund, Verläufe und Schatten.

---

## 🔢 Weitere Tokens

**Einzige Wertequelle im Code ist `frontend/src/theme.ts`**, gespeist aus den hellen Werten der Vorlage: Palette, Schatten, Radien, Grund, Schriften, Tabellenziffern und Flächen der gefüllten Meldungen. Abgeleitete Farbzuordnungen (etwa Status- und Kennzeichenfarben) liegen in eigenen Modulen unter `frontend/src/lib/` und bilden auf die Melder und Schild-Töne der Vorlage ab. Diese Datei nennt außer der Palettentabelle der Vorlage keine Tokenwerte.

---

## 📜 Historie

- **seit 2026-09-17 „Kupferwarte":** Die Designsprache stammt aus dem Repo kanban-kit (Designsession am 2026-09-16) und gilt seit dem 2026-09-17 unverändert für fb.crm; die Vorlage `docs/entwurf-leitstand.html` ist verbindlich (Entscheidung Manne).
- **Die Lehre für jeden Gestaltungswechsel:** Zuerst wird **diese Datei** umgestellt, dann geplant und umgesetzt — ein Plan folgt der Quelle, die im Repository als bindend markiert ist.
