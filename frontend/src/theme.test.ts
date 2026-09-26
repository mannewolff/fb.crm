import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { contrastRatio } from './lib/contrast';
import {
  KUPFERWOLKE,
  RADIUS_GROSS,
  RADIUS_KACHEL,
  RADIUS_KLEIN,
  RADIUS_MAL,
  RADIUS_MITTEL,
  RADIUS_RUND,
  SCHATTEN,
  theme,
} from './theme';

/** Die drei Flaechen der Kupferwolke, auf denen Text stehen kann. */
const FLAECHEN = ['grund', 'flaeche', 'flaecheWeich'] as const;

/**
 * Fliesstext: 4,5:1 (WCAG AA). Die drei Textstufen und die Leitfarbe tragen Schrift in Groessen
 * unter 18 px — die Ausnahme fuer grossen Text greift nirgends.
 */
const FLIESSTEXT = ['text', 'textMatt', 'textSchwach', 'kupfer'] as const;

/** Die helle Palette — das einzige Erscheinungsbild (CLAUDE-design.md, „Erscheinungsbild"). */
const p = KUPFERWOLKE;

const TOENUNGEN = ['pfirsich', 'salbei', 'himmel', 'bernstein', 'rose', 'flieder'] as const;
const MELDER = ['gruen', 'bernstein', 'zinnober', 'stahl', 'grau'] as const;

describe('Kontrast', () => {
  it.each(
    FLIESSTEXT.flatMap((rolle) => FLAECHEN.map((flaeche) => [rolle, flaeche] as const)),
  )('haelt %s auf %s bei mindestens 4,5:1', (rolle, flaeche) => {
    expect(contrastRatio(p[rolle], p[flaeche])).toBeGreaterThanOrEqual(4.5);
  });

  it('haelt Kupfer auf Pfirsich bei mindestens 4,5:1 — die Leitfarbe steht auch auf der Toenung', () => {
    expect(contrastRatio(p.kupfer, p.toenung.pfirsich.flaeche)).toBeGreaterThanOrEqual(4.5);
  });

  it('haelt den Rand starker Umrisse auf der weichen Flaeche bei mindestens 3:1', () => {
    // WCAG 1.4.11: Umrisse von Bedienelementen. Gerechnet gegen die schwaechste Flaeche, auf
    // der ein Eingabefeld oder eine weiche Taste steht.
    expect(contrastRatio(p.randStark, p.flaecheWeich)).toBeGreaterThanOrEqual(3);
  });

  it('haelt Weiss auf beiden Enden der Kupfertaste bei mindestens 4,5:1', () => {
    expect(contrastRatio(p.kupferSchrift, p.kupferTaste)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(p.kupferSchrift, p.kupferTief)).toBeGreaterThanOrEqual(4.5);
  });

  it.each(TOENUNGEN)('haelt die Schrift der Toenung %s auf ihrer Flaeche bei mindestens 4,5:1', (name) => {
    const toenung = p.toenung[name];
    expect(contrastRatio(toenung.schrift, toenung.flaeche)).toBeGreaterThanOrEqual(4.5);
  });

  it.each(MELDER)('haelt den Melder %s auf der Flaeche bei mindestens 4,5:1', (name) => {
    expect(contrastRatio(p.melder[name], p.flaeche)).toBeGreaterThanOrEqual(4.5);
  });

  it('staffelt die drei Textstufen auf der schwaechsten Flaeche unterscheidbar', () => {
    // Ohne diese Gegenprobe koennte ein Nachdunkeln zweier Stufen sie zusammenfallen lassen,
    // und die Designquelle verlore eine ihrer drei Textstufen.
    const text = contrastRatio(p.text, p.grund);
    const matt = contrastRatio(p.textMatt, p.grund);
    const schwach = contrastRatio(p.textSchwach, p.grund);
    expect(text).toBeGreaterThan(matt);
    expect(matt).toBeGreaterThan(schwach);
  });
});

describe('Tokens der Vorlage', () => {
  it('traegt die Radien 28, 26, 24, 22 und 14 px und die runde Form', () => {
    expect(RADIUS_GROSS).toBe(28);
    expect(RADIUS_MAL).toBe(26);
    expect(RADIUS_KACHEL).toBe(24);
    expect(RADIUS_MITTEL).toBe(22);
    expect(RADIUS_KLEIN).toBe(14);
    expect(RADIUS_RUND).toBe(999);
    expect(theme.shape.borderRadius).toBe(RADIUS_KLEIN);
  });

  it('kennt genau drei Schattenstufen', () => {
    expect(Object.keys(SCHATTEN)).toEqual(['karte', 'hoch', 'kupfer']);
  });

  it('schreibt die Schattenfarbe als warmes Braun, nicht als Blaugrau oder Schwarz', () => {
    expect(SCHATTEN.karte).toContain('rgba(80,50,30,');
    expect(SCHATTEN.hoch).toContain('rgba(80,50,30,');
    expect(SCHATTEN.kupfer).toContain(p.kupferSchatten);
  });

  it('schreibt die CSS-Variablen mit dem Praefix fb', () => {
    expect(theme.cssVarPrefix).toBe('fb');
  });

  it('kennt nur das helle Farbschema', () => {
    expect(Object.keys(theme.colorSchemes)).toEqual(['light']);
  });

  it('erzeugt kein CSS unter prefers-color-scheme — die Oberflaeche bleibt hell, gleich was der Rechner sagt', () => {
    // Gegenprobe zum Ausbau (Issue #34): ohne sie waere ein still wieder eingeschalteter
    // Dunkelsatz von einem wirksamen Ausbau nicht zu unterscheiden. Geprueft werden das
    // erzeugte Stylesheet der CSS-Variablen und die globalen Regeln der Komponenten.
    expect(JSON.stringify(theme.generateStyleSheets())).not.toContain('prefers-color-scheme');
    expect(JSON.stringify(theme.components)).not.toContain('prefers-color-scheme');
  });

  it('setzt den Umbruchpunkt sm auf 760 px', () => {
    expect(theme.breakpoints.values.sm).toBe(760);
  });

  it('legt beide Schimmer in den Grund: Kupfer-Glanz oben links, Flieder oben rechts', () => {
    expect(p.grundVerlauf).toContain('12% -10%');
    expect(p.grundVerlauf).toContain('100% 0%');
    expect(p.grundVerlauf).toContain(p.grund);
  });

  it('fuehrt Plus Jakarta Sans als einzige Schrift', () => {
    expect(theme.typography.fontFamily).toContain('Plus Jakarta Sans');
    expect(theme.typography.h1.fontFamily).toContain('Plus Jakarta Sans');
  });

  it('setzt das Etikett in Satzschreibung ohne Laufweite', () => {
    expect(theme.typography.overline.textTransform).toBe('none');
    expect(theme.typography.overline.letterSpacing).toBe('normal');
  });
});

/**
 * Der Quelltext traegt nichts mehr aus der Kupferwarte.
 *
 * Eine Gegenprobe zum Umbau (Issue #76): Ohne sie bliebe ein vergessener Verbraucher
 * unbemerkt, solange er nur uebersetzt — ein Mischzustand aus zwei Paletten faellt in
 * keinem anderen Test auf.
 */
const QUELLE = join(process.cwd(), 'src');
const ABGELEGT = /kupferwarte|Archivo|Plex|monoFontFamily/;

function quelldateien(verzeichnis: string): readonly string[] {
  return readdirSync(verzeichnis, { withFileTypes: true }).flatMap((eintrag) => {
    const pfad = join(verzeichnis, eintrag.name);
    if (eintrag.isDirectory()) {
      return quelldateien(pfad);
    }
    if (!/\.tsx?$/.test(eintrag.name) || /\.test\.tsx?$/.test(eintrag.name)) {
      return [];
    }
    return [pfad];
  });
}

describe('Kupferwarte abgelegt', () => {
  it('findet im Quelltext weder kupferwarte noch Archivo, Plex oder monoFontFamily', () => {
    const treffer = quelldateien(QUELLE).filter((pfad) =>
      ABGELEGT.test(readFileSync(pfad, 'utf8')),
    );
    expect(treffer).toEqual([]);
  });
});
