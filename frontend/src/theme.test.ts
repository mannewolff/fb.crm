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

describe.each([
  ['hell', KUPFERWARTE.hell],
  ['dunkel', KUPFERWARTE.dunkel],
])('Kontrast im Erscheinungsbild %s', (_name, p) => {
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

  it('haelt die Schrift auf der Kupferfuellung bei mindestens 4,5:1', () => {
    expect(contrastRatio(p.kupferSchrift, p.kupfer)).toBeGreaterThanOrEqual(4.5);
  });

  it('staffelt die drei Textstufen auf der schwaechsten Flaeche unterscheidbar', () => {
    // Nachgezogen wird nur nach unten: waere textSchwach bloss auf 4,5:1 gehoben, fiele es
    // mit textMatt zusammen und die Vorlage verlore eine ihrer drei Stufen.
    const schwaechste = _name === 'hell' ? p.nute : p.platteHoch;
    const text = contrastRatio(p.text, schwaechste);
    const matt = contrastRatio(p.textMatt, schwaechste);
    const schwach = contrastRatio(p.textSchwach, schwaechste);
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

  it('kennt genau vier Schattenstufen in beiden Erscheinungsbildern', () => {
    expect(Object.keys(SCHATTEN.hell)).toEqual(['nute', 'platte', 'hoch', 'taste']);
    expect(Object.keys(SCHATTEN.dunkel)).toEqual(['nute', 'platte', 'hoch', 'taste']);
  });

  it('schreibt die CSS-Variablen mit dem Praefix fb', () => {
    expect(theme.cssVarPrefix).toBe('fb');
  });

  it('schaltet das Erscheinungsbild ueber prefers-color-scheme, nicht ueber ein Bedienelement', () => {
    expect(theme.colorSchemeSelector).toBe('media');
  });

  it('setzt den Umbruchpunkt sm auf 760 px', () => {
    expect(theme.breakpoints.values.sm).toBe(760);
  });

  it('legt den Kupfer-Schimmer oben links in den Grund beider Erscheinungsbilder', () => {
    for (const p of [KUPFERWARTE.hell, KUPFERWARTE.dunkel]) {
      expect(p.grundVerlauf).toContain('radial-gradient');
      expect(p.grundVerlauf).toContain('18% -8%');
      expect(p.grundVerlauf).toContain(p.kupferSchimmer);
    }
  });
});
