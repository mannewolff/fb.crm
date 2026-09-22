import { describe, expect, it } from 'vitest';

import { contrastRatio } from './lib/contrast';
import {
  CARD_RADIUS,
  CONTROL_RADIUS,
  KUPFERWARTE,
  PANEL_RADIUS,
  SCHATTEN,
  theme,
} from './theme';

/** Die sechs Flaechen, auf denen in der Kupferwarte etwas stehen kann. */
const FLAECHEN = ['grund', 'grundTief', 'nute', 'platte', 'platteFuss', 'platteHoch'] as const;

/**
 * Fliesstext: 4,5:1 (WCAG AA). Die drei Textstufen der Vorlage tragen Schrift in
 * Groessen unter 18 px — die Ausnahme fuer grosse Schrift greift nirgends.
 */
const FLIESSTEXT = ['text', 'textMatt', 'textSchwach'] as const;

/**
 * Bedeutungstragende Grafik: 3:1 (WCAG 1.4.11). Kupfer traegt Symbol, Fuellung und
 * Fokusring; die fuenf Melder tragen einen Zustand als Fuellung oder Plakette.
 * Nicht in der Tabelle stehen `rand`, `randStark`, `kante` und `kupferHell`: Haarlinien,
 * Tastenkappen, Lichtkanten und Verlaufsenden tragen keine Aussage und fallen damit
 * nicht unter 1.4.11.
 */
const GRAFIK = ['kupfer', 'gruen', 'bernst', 'zinnob', 'stahl', 'grau'] as const;

/** Die helle Palette — das einzige Erscheinungsbild (CLAUDE-design.md, „Erscheinungsbild"). */
const p = KUPFERWARTE;

describe('Kontrast', () => {
  it.each(
    FLIESSTEXT.flatMap((rolle) => FLAECHEN.map((flaeche) => [rolle, flaeche] as const)),
  )('haelt %s auf %s bei mindestens 4,5:1', (rolle, flaeche) => {
    expect(contrastRatio(p[rolle], p[flaeche])).toBeGreaterThanOrEqual(4.5);
  });

  it.each(GRAFIK.flatMap((rolle) => FLAECHEN.map((flaeche) => [rolle, flaeche] as const)))(
    'haelt %s auf %s bei mindestens 3:1',
    (rolle, flaeche) => {
      expect(contrastRatio(p[rolle], p[flaeche])).toBeGreaterThanOrEqual(3);
    },
  );

  it('haelt das Kuerzel im Nutzer-Mal auf beiden Enden des Verlaufs bei mindestens 4,5:1', () => {
    expect(contrastRatio(p.nutzerSchrift, p.nutzerHell)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(p.nutzerSchrift, p.nutzerTief)).toBeGreaterThanOrEqual(4.5);
  });

  it('haelt die Schrift auf der Kupferfuellung bei mindestens 4,5:1', () => {
    expect(contrastRatio(p.kupferSchrift, p.kupfer)).toBeGreaterThanOrEqual(4.5);
  });

  it('staffelt die drei Textstufen auf der schwaechsten Flaeche unterscheidbar', () => {
    // Nachgezogen wird nur nach unten: waere textSchwach bloss auf 4,5:1 gehoben, fiele es
    // mit textMatt zusammen und die Vorlage verlore eine ihrer drei Stufen.
    const text = contrastRatio(p.text, p.nute);
    const matt = contrastRatio(p.textMatt, p.nute);
    const schwach = contrastRatio(p.textSchwach, p.nute);
    expect(text).toBeGreaterThan(matt);
    expect(matt).toBeGreaterThan(schwach);
    expect(matt - schwach).toBeGreaterThan(0.5);
  });
});

describe('Tokens der Vorlage', () => {
  it('traegt die drei Radien 14, 10 und 6 px', () => {
    expect(PANEL_RADIUS).toBe(14);
    expect(CARD_RADIUS).toBe(10);
    expect(CONTROL_RADIUS).toBe(6);
    expect(theme.shape.borderRadius).toBe(CONTROL_RADIUS);
  });

  it('kennt genau vier Schattenstufen', () => {
    expect(Object.keys(SCHATTEN)).toEqual(['nute', 'platte', 'hoch', 'taste']);
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

  it('legt den Kupfer-Schimmer oben links in den Grund', () => {
    expect(p.grundVerlauf).toContain('radial-gradient');
    expect(p.grundVerlauf).toContain('18% -8%');
    expect(p.grundVerlauf).toContain(p.kupferSchimmer);
  });
});
